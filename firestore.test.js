const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-rrremind-1d7fcad6-b298-4462-b625-7de65ff4b83c";

const OWNER_UID = "owner_uid_101";
const OTHER_UID = "other_uid_202";
const ADMIN_UID = "admin_uid_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function dbFor(ctx) {
  return ctx.firestore({ databaseId: DATABASE_ID });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedAdminConfig() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await dbFor(ctx).collection("admin_roles").doc("config").set({
      primary_admin_uid: ADMIN_UID,
      authorized_admin_uids: [ADMIN_UID],
    });
  });
}

async function seedOwnerDevice(deviceId = "dev_owner_01") {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = pastDate();
    await dbFor(ctx).collection("devices").doc(deviceId).set({
      deviceId,
      ownerUid: OWNER_UID,
      deviceModel: "Pixel 9",
      fcmToken: "fcm_tok_1",
      status: "ACTIVE",
      appVersion: "1.0",
      registeredAt: now,
      lastActiveAt: now,
    });
  });
}

// --- 1. UNAUTHENTICATED DENIALS ---

test("Unauthenticated user: denied read/write across all collections", async () => {
  await seedAdminConfig();
  await seedOwnerDevice("dev_owner_01");
  const unauthDb = dbFor(testEnv.unauthenticatedContext());

  await assertFails(unauthDb.collection("admin_roles").doc("config").get());
  await assertFails(unauthDb.collection("devices").get());
  await assertFails(unauthDb.collection("devices").doc("dev_owner_01").get());
  await assertFails(unauthDb.collection("devices").doc("dev_owner_01").collection("pending_commands").get());
  await assertFails(unauthDb.collection("users").doc(OWNER_UID).get());
  await assertFails(unauthDb.collection("security_events").get());
});

// --- 2. ADMIN ROLES PROTECTION ---

test("Admin roles: non-admin cannot read or write /admin_roles/config; admin can read only", async () => {
  await seedAdminConfig();
  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const adminDb = dbFor(testEnv.authenticatedContext(ADMIN_UID));

  await assertFails(ownerDb.collection("admin_roles").doc("config").get());
  await assertFails(ownerDb.collection("admin_roles").get());
  await assertFails(ownerDb.collection("admin_roles").doc("config").set({ authorized_admin_uids: [OWNER_UID] }));
  await assertFails(ownerDb.collection("admin_roles").doc("config").update({ primary_admin_uid: OWNER_UID }));

  await assertSucceeds(adminDb.collection("admin_roles").doc("config").get());
  await assertFails(adminDb.collection("admin_roles").doc("config").update({ primary_admin_uid: OTHER_UID }));
  await assertFails(adminDb.collection("admin_roles").doc("config").delete());
});

// --- 3. DEVICES REGISTRATION & ISOLATION ---

test("Devices: owner can register and update own device, cannot reassign ownerUid or registeredAt or inject ghost fields", async () => {
  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const otherDb = dbFor(testEnv.authenticatedContext(OTHER_UID));
  const now = pastDate();

  // Check existence before creating device (matches DeviceRegistrationManager)
  await assertSucceeds(ownerDb.collection("devices").doc("dev_owner_01").get());

  // Valid create
  await assertSucceeds(
    ownerDb.collection("devices").doc("dev_owner_01").set({
      deviceId: "dev_owner_01",
      ownerUid: OWNER_UID,
      deviceModel: "Pixel 9",
      fcmToken: "tok_abc",
      status: "ACTIVE",
      appVersion: "1.0",
      registeredAt: now,
      lastActiveAt: now,
    })
  );

  // Ghost field denied
  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_02").set({
      deviceId: "dev_owner_02",
      ownerUid: OWNER_UID,
      deviceModel: "Pixel 9",
      fcmToken: "tok_abc",
      status: "ACTIVE",
      registeredAt: now,
      lastActiveAt: now,
      isAdminOverride: true,
    })
  );

  // Cross-user read & update denied
  await assertFails(otherDb.collection("devices").doc("dev_owner_01").get());
  await assertFails(otherDb.collection("devices").doc("dev_owner_01").update({ fcmToken: "hijacked" }));
  await assertFails(otherDb.collection("devices").get());

  // Owner cannot forge another user's ownerUid
  await assertFails(
    otherDb.collection("devices").doc("dev_forged").set({
      deviceId: "dev_forged",
      ownerUid: OWNER_UID,
      deviceModel: "Evil Phone",
      fcmToken: "tok",
      status: "ACTIVE",
      registeredAt: now,
      lastActiveAt: now,
    })
  );

  // Owner can query own devices with matching filter
  await assertSucceeds(ownerDb.collection("devices").where("ownerUid", "==", OWNER_UID).get());

  // Owner can update allowed mutable fields without touching registeredAt
  await assertSucceeds(
    ownerDb.collection("devices").doc("dev_owner_01").update({
      fcmToken: "tok_rotated",
      lastActiveAt: pastDate(),
    })
  );

  // Owner cannot overwrite registeredAt
  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_01").update({
      registeredAt: pastDate(),
    })
  );
});

// --- 4. REMOTE PENDING COMMANDS & TERMINAL STATE LOCKING ---

test("Pending commands: non-admin & device owner CANNOT create commands; only admin can create", async () => {
  await seedAdminConfig();
  await seedOwnerDevice("dev_owner_01");

  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const otherDb = dbFor(testEnv.authenticatedContext(OTHER_UID));
  const adminDb = dbFor(testEnv.authenticatedContext(ADMIN_UID));

  const cmdPayload = {
    commandId: "cmd_001",
    ownerUid: OWNER_UID,
    issuedByUid: ADMIN_UID,
    action: "LOCKDOWN",
    status: "PENDING",
    expiresAt: Date.now() + 86400000,
    createdAt: pastDate(),
    reason: "Stolen device",
  };

  // Device owner cannot issue remote commands to themselves
  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_001").set({
      ...cmdPayload,
      issuedByUid: OWNER_UID,
    })
  );

  // Random user cannot issue commands
  await assertFails(
    otherDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_001").set({
      ...cmdPayload,
      issuedByUid: OTHER_UID,
    })
  );

  // Admin CAN create valid command
  await assertSucceeds(
    adminDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_001").set(cmdPayload)
  );
});

test("Pending commands: client query alignment + one-time terminal state acknowledgment", async () => {
  await seedAdminConfig();
  await seedOwnerDevice("dev_owner_01");

  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const otherDb = dbFor(testEnv.authenticatedContext(OTHER_UID));
  const adminDb = dbFor(testEnv.authenticatedContext(ADMIN_UID));

  await assertSucceeds(
    adminDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_lock_1").set({
      commandId: "cmd_lock_1",
      ownerUid: OWNER_UID,
      issuedByUid: ADMIN_UID,
      action: "LOCKDOWN",
      status: "PENDING",
      expiresAt: Date.now() + 3600000,
      createdAt: pastDate(),
      reason: "Verification test",
    })
  );

  // Client query matching CommandSyncWorker
  await assertSucceeds(
    ownerDb.collection("devices").doc("dev_owner_01").collection("pending_commands")
      .where("ownerUid", "==", OWNER_UID)
      .where("status", "==", "PENDING")
      .get()
  );

  // Other user cannot list owner's pending_commands
  await assertFails(
    otherDb.collection("devices").doc("dev_owner_01").collection("pending_commands")
      .where("ownerUid", "==", OWNER_UID)
      .get()
  );

  // Owner cannot alter action field during update
  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_lock_1").update({
      action: "PING",
      status: "COMPLETED",
      acknowledgedAt: pastDate(),
    })
  );

  // Owner CAN acknowledge PENDING -> COMPLETED once
  await assertSucceeds(
    ownerDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_lock_1").update({
      status: "COMPLETED",
      acknowledgedAt: pastDate(),
    })
  );

  // Terminal state lock: owner CANNOT transition COMPLETED -> PENDING or update again
  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_lock_1").update({
      status: "PENDING",
      acknowledgedAt: pastDate(),
    })
  );

  // Owner cannot delete pending commands
  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_01").collection("pending_commands").doc("cmd_lock_1").delete()
  );
});

// --- 5. COMMAND HISTORY IMMUTABILITY ---

test("Command history: append-only by admin; owner can read own history; updates/deletes denied", async () => {
  await seedAdminConfig();
  await seedOwnerDevice("dev_owner_01");

  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const adminDb = dbFor(testEnv.authenticatedContext(ADMIN_UID));

  await assertFails(
    ownerDb.collection("devices").doc("dev_owner_01").collection("command_history").doc("hist_1").set({
      commandId: "hist_1",
      ownerUid: OWNER_UID,
      issuedByUid: OWNER_UID,
      action: "PING",
      status: "COMPLETED",
      acknowledgedAt: pastDate(),
    })
  );

  await assertSucceeds(
    adminDb.collection("devices").doc("dev_owner_01").collection("command_history").doc("hist_1").set({
      commandId: "hist_1",
      ownerUid: OWNER_UID,
      issuedByUid: ADMIN_UID,
      action: "PING",
      status: "COMPLETED",
      acknowledgedAt: pastDate(),
    })
  );

  await assertSucceeds(
    ownerDb.collection("devices").doc("dev_owner_01").collection("command_history")
      .where("ownerUid", "==", OWNER_UID)
      .get()
  );

  await assertFails(
    adminDb.collection("devices").doc("dev_owner_01").collection("command_history").doc("hist_1").update({
      status: "FAILED",
    })
  );
  await assertFails(
    adminDb.collection("devices").doc("dev_owner_01").collection("command_history").doc("hist_1").delete()
  );
});

// --- 6. USER SETTINGS & SECURITY EVENTS ---

test("Users & settings: strict UID isolation and validation bounds", async () => {
  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const otherDb = dbFor(testEnv.authenticatedContext(OTHER_UID));

  await assertSucceeds(
    ownerDb.collection("users").doc(OWNER_UID).collection("settings").doc("security_profile").set({
      securityLevel: "HIGH",
      maxFailures: 5,
      lockoutSeconds: 60,
      notificationsEnabled: true,
      autoLockdownOnTamper: true,
      updatedAt: pastDate(),
    })
  );

  // Invalid bounds rejected
  await assertFails(
    ownerDb.collection("users").doc(OWNER_UID).collection("settings").doc("security_profile").set({
      securityLevel: "INVALID_LVL",
      maxFailures: 1,
      lockoutSeconds: 5,
      updatedAt: pastDate(),
    })
  );

  // Cross-user settings read/write denied
  await assertFails(
    otherDb.collection("users").doc(OWNER_UID).collection("settings").doc("security_profile").get()
  );
});

test("Security events: owner can create & list own events, cannot spoof another userId or tamper history", async () => {
  const ownerDb = dbFor(testEnv.authenticatedContext(OWNER_UID));
  const otherDb = dbFor(testEnv.authenticatedContext(OTHER_UID));

  await assertSucceeds(
    ownerDb.collection("security_events").doc("evt_01").set({
      eventId: "evt_01",
      userId: OWNER_UID,
      deviceId: "dev_owner_01",
      eventType: "PIN_VERIFIED",
      category: "AUTHENTICATION",
      severity: "INFO",
      timestamp: pastDate(),
      detectionSource: "AppLock",
      actionTaken: "LOGGED_ONLY",
      details: { source: "biometric" },
    })
  );

  // Spoofed userId denied
  await assertFails(
    otherDb.collection("security_events").doc("evt_spoof").set({
      eventId: "evt_spoof",
      userId: OWNER_UID,
      eventType: "SPOOF",
      category: "SYSTEM",
      severity: "HIGH",
      timestamp: pastDate(),
      detectionSource: "Spoof",
      actionTaken: "BLOCKED",
    })
  );

  // Owner can query own security events
  await assertSucceeds(
    ownerDb.collection("security_events").where("userId", "==", OWNER_UID).get()
  );

  // Owner cannot alter or delete logged events
  await assertFails(ownerDb.collection("security_events").doc("evt_01").update({ severity: "LOW" }));
  await assertFails(ownerDb.collection("security_events").doc("evt_01").delete());
});
