import * as crypto from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";

initializeApp();

const DATABASE_ID = "ai-studio-android-rrremind-1d7fcad6-b298-4462-b625-7de65ff4b83c";
const db = getFirestore(DATABASE_ID);

const ALLOWED_ACTIONS = new Set(["LOCKDOWN", "RELEASE_LOCKDOWN", "SYNC_SETTINGS", "PING"]);
const ALLOWED_SECURITY_LEVELS = new Set(["STANDARD", "HIGH", "MAXIMUM"]);
const VALID_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;

interface SendCommandRequest {
  deviceId?: unknown;
  action?: unknown;
  reason?: unknown;
  ttlHours?: unknown;
  securityLevel?: unknown;
  maxFailures?: unknown;
  lockoutSeconds?: unknown;
  autoLockdownOnTamper?: unknown;
}

interface AcknowledgeCommandRequest {
  deviceId?: unknown;
  commandId?: unknown;
  status?: unknown;
  error?: unknown;
}

async function assertAuthorizedAdmin(uid: string, tokenClaims?: Record<string, unknown>): Promise<void> {
  if (tokenClaims?.admin === true) {
    return;
  }

  const configSnap = await db.collection("admin_roles").doc("config").get();
  if (!configSnap.exists) {
    throw new HttpsError(
      "permission-denied",
      "Administrator roles have not been configured in /admin_roles/config."
    );
  }

  const data = configSnap.data() ?? {};
  const primaryUid = typeof data.primary_admin_uid === "string" ? data.primary_admin_uid : null;
  const authorizedUids = Array.isArray(data.authorized_admin_uids) ? data.authorized_admin_uids : [];

  if (primaryUid === uid || authorizedUids.includes(uid)) {
    return;
  }

  throw new HttpsError(
    "permission-denied",
    "Caller UID is not authorized to issue remote administrative commands."
  );
}

/**
 * Server-controlled administrative command dispatcher.
 * Validates admin identity via custom claim OR /admin_roles/config, writes a UUID-keyed
 * expiring command into /devices/{deviceId}/pending_commands/{commandId}, and dispatches
 * a high-priority data-only FCM message when an active registration token is present.
 */
export const sendProtectionCommand = onCall<SendCommandRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }

  const callerUid = request.auth.uid;
  await assertAuthorizedAdmin(callerUid, request.auth.token as Record<string, unknown>);

  const payload = request.data ?? {};
  const deviceId = typeof payload.deviceId === "string" ? payload.deviceId.trim() : "";
  const action = typeof payload.action === "string" ? payload.action.trim().toUpperCase() : "";

  if (!VALID_ID_REGEX.test(deviceId)) {
    throw new HttpsError("invalid-argument", "Invalid target deviceId.");
  }
  if (!ALLOWED_ACTIONS.has(action)) {
    throw new HttpsError("invalid-argument", `Unsupported command action: ${action}`);
  }

  const deviceRef = db.collection("devices").doc(deviceId);
  const deviceSnap = await deviceRef.get();
  if (!deviceSnap.exists) {
    throw new HttpsError("not-found", `Target device ${deviceId} is not registered.`);
  }

  const deviceData = deviceSnap.data() ?? {};
  if (deviceData.status === "REVOKED") {
    throw new HttpsError("failed-precondition", "Cannot send commands to a revoked device.");
  }

  const ownerUid = typeof deviceData.ownerUid === "string" ? deviceData.ownerUid : "";
  if (!ownerUid) {
    throw new HttpsError("failed-precondition", "Target device record is missing ownerUid.");
  }

  const rawTtl = typeof payload.ttlHours === "number" ? payload.ttlHours : 24;
  const ttlHours = Math.min(72, Math.max(1, Math.round(rawTtl)));
  const expiresAt = Date.now() + ttlHours * 60 * 60 * 1000;
  const commandId = `cmd_${crypto.randomUUID().replace(/-/g, "")}`;

  const commandDoc: Record<string, unknown> = {
    commandId,
    ownerUid,
    issuedByUid: callerUid,
    action,
    status: "PENDING",
    expiresAt,
    createdAt: FieldValue.serverTimestamp(),
  };

  const fcmDataPayload: Record<string, string> = {
    commandId,
    action,
    expiresAt: String(expiresAt),
  };

  if (typeof payload.reason === "string" && payload.reason.trim().length > 0) {
    const cleanReason = payload.reason.trim().slice(0, 240);
    commandDoc.reason = cleanReason;
    fcmDataPayload.reason = cleanReason;
  }

  if (action === "SYNC_SETTINGS") {
    const level = typeof payload.securityLevel === "string"
      ? payload.securityLevel.trim().toUpperCase()
      : "STANDARD";
    if (!ALLOWED_SECURITY_LEVELS.has(level)) {
      throw new HttpsError("invalid-argument", "Invalid securityLevel value.");
    }
    const maxFailures = typeof payload.maxFailures === "number"
      ? Math.min(10, Math.max(2, Math.round(payload.maxFailures)))
      : 5;
    const lockoutSeconds = typeof payload.lockoutSeconds === "number"
      ? Math.min(600, Math.max(30, Math.round(payload.lockoutSeconds)))
      : 60;
    const autoLockdownOnTamper = typeof payload.autoLockdownOnTamper === "boolean"
      ? payload.autoLockdownOnTamper
      : true;

    commandDoc.securityLevel = level;
    commandDoc.maxFailures = maxFailures;
    commandDoc.lockoutSeconds = lockoutSeconds;
    commandDoc.autoLockdownOnTamper = autoLockdownOnTamper;

    fcmDataPayload.securityLevel = level;
    fcmDataPayload.maxFailures = String(maxFailures);
    fcmDataPayload.lockoutSeconds = String(lockoutSeconds);
    fcmDataPayload.autoLockdownOnTamper = String(autoLockdownOnTamper);
  }

  await deviceRef.collection("pending_commands").doc(commandId).set(commandDoc);

  let fcmDispatched = false;
  const fcmToken = typeof deviceData.fcmToken === "string" ? deviceData.fcmToken.trim() : "";
  if (fcmToken.length > 0) {
    try {
      await getMessaging().send({
        token: fcmToken,
        data: fcmDataPayload,
        android: {
          priority: "high",
          ttl: ttlHours * 60 * 60 * 1000,
        },
      });
      fcmDispatched = true;
    } catch (err) {
      logger.warn("FCM push failed; command remains queued for WorkManager recovery", {
        deviceId,
        commandId,
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  return {
    commandId,
    deviceId,
    action,
    expiresAt,
    fcmDispatched,
  };
});

/**
 * Atomically transitions a PENDING command to a terminal status and writes an
 * immutable audit record to /devices/{deviceId}/command_history/{commandId}.
 */
export const acknowledgeCommand = onCall<AcknowledgeCommandRequest>(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Firebase Authentication is required.");
  }

  const callerUid = request.auth.uid;
  const payload = request.data ?? {};
  const deviceId = typeof payload.deviceId === "string" ? payload.deviceId.trim() : "";
  const commandId = typeof payload.commandId === "string" ? payload.commandId.trim() : "";
  const status = typeof payload.status === "string" ? payload.status.trim().toUpperCase() : "";

  if (!VALID_ID_REGEX.test(deviceId) || !VALID_ID_REGEX.test(commandId)) {
    throw new HttpsError("invalid-argument", "Invalid deviceId or commandId.");
  }
  if (!["COMPLETED", "EXPIRED", "FAILED"].includes(status)) {
    throw new HttpsError("invalid-argument", "Terminal status must be COMPLETED, EXPIRED, or FAILED.");
  }

  const deviceRef = db.collection("devices").doc(deviceId);
  const pendingRef = deviceRef.collection("pending_commands").doc(commandId);
  const historyRef = deviceRef.collection("command_history").doc(commandId);

  await db.runTransaction(async (tx) => {
    const deviceSnap = await tx.get(deviceRef);
    if (!deviceSnap.exists) {
      throw new HttpsError("not-found", "Device does not exist.");
    }
    const ownerUid = deviceSnap.get("ownerUid");
    if (ownerUid !== callerUid) {
      await assertAuthorizedAdmin(callerUid, request.auth?.token as Record<string, unknown>);
    }

    const pendingSnap = await tx.get(pendingRef);
    if (!pendingSnap.exists) {
      throw new HttpsError("not-found", "Pending command not found.");
    }
    const cmdData = pendingSnap.data() ?? {};
    if (cmdData.status !== "PENDING") {
      return;
    }

    const nowTs = FieldValue.serverTimestamp();
    const errorMsg = typeof payload.error === "string" ? payload.error.slice(0, 500) : null;

    tx.update(pendingRef, {
      status,
      acknowledgedAt: nowTs,
      ...(errorMsg ? { error: errorMsg } : {}),
    });

    tx.set(historyRef, {
      commandId,
      ownerUid: cmdData.ownerUid,
      issuedByUid: cmdData.issuedByUid,
      action: cmdData.action,
      status,
      acknowledgedAt: nowTs,
    });
  });

  return { commandId, status };
});

/**
 * Scheduled cleanup marking stale PENDING commands past expiresAt as EXPIRED.
 */
export const expireStalePendingCommands = onSchedule("every 24 hours", async () => {
  const now = Date.now();
  const staleSnap = await db
    .collectionGroup("pending_commands")
    .where("status", "==", "PENDING")
    .where("expiresAt", "<", now)
    .limit(200)
    .get();

  if (staleSnap.empty) {
    return;
  }

  const batch = db.batch();
  for (const doc of staleSnap.docs) {
    const cmdData = doc.data();
    const deviceRef = doc.ref.parent.parent;
    const nowTs = FieldValue.serverTimestamp();
    batch.update(doc.ref, {
      status: "EXPIRED",
      acknowledgedAt: nowTs,
      error: "Command expired server-side before delivery",
    });
    if (deviceRef) {
      batch.set(deviceRef.collection("command_history").doc(doc.id), {
        commandId: cmdData.commandId ?? doc.id,
        ownerUid: cmdData.ownerUid ?? "",
        issuedByUid: cmdData.issuedByUid ?? "system",
        action: cmdData.action ?? "PING",
        status: "EXPIRED",
        acknowledgedAt: nowTs,
      });
    }
  }
  await batch.commit();
});
