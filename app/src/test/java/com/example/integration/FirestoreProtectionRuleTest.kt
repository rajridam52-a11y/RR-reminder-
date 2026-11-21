package com.example.integration

import com.example.base.FirestoreEmulatorTestBase
import com.example.integration.protection.CommandProcessor
import com.example.integration.protection.DeviceRegistrationManager
import com.example.integration.protection.EmergencyLockdownManager
import com.example.integration.protection.RemoteSettingsSyncManager
import com.example.security.backend.OwnerSecurityBridge
import com.example.security.events.SecurityAction
import com.example.security.events.SecurityEvent
import com.example.security.events.SecurityEventCategory
import com.example.security.events.SecurityEventManager
import com.example.security.events.SecurityRiskSeverity
import com.example.security.events.VerificationStatus
import com.example.security.settings.SecuritySettingsManager
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FirestoreProtectionRuleTest : FirestoreEmulatorTestBase() {

    @Before
    fun initAppManagers() {
        SecuritySettingsManager.initialize(appContext)
        SecurityEventManager.initialize(appContext)
        DeviceRegistrationManager.initialize(appContext)
        EmergencyLockdownManager.initialize(appContext)
        RemoteSettingsSyncManager.initialize(appContext)
    }

    @Test
    fun unauthenticatedCaller_cannotQueryDevicesPendingCommandsOrSecurityEvents() = runBlocking {
        auth.signOut()
        val deviceId = "dev_unauth_${UUID.randomUUID()}"

        val deviceQueryEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("devices").get().await()
            }
        }
        assertThat(deviceQueryEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        val pendingCmdEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("devices")
                    .document(deviceId)
                    .collection("pending_commands")
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .await()
            }
        }
        assertThat(pendingCmdEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        val eventsEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("security_events").get().await()
            }
        }
        assertThat(eventsEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    @Test
    fun authenticatedOwner_canRegisterAndUpdateOwnDeviceAndSyncSettings() = runBlocking {
        val ownerUid = signInTestUser(OWNER_EMAIL)

        val firstReg = withTimeout(DEFAULT_TIMEOUT_MS) {
            DeviceRegistrationManager.registerOrSyncDevice(appContext, firestoreOverride = firestore)
        }
        assertThat(firstReg.isSuccess).isTrue()

        // Second sync updates mutable fields (fcmToken/lastActiveAt) while preserving immutable registeredAt
        DeviceRegistrationManager.updateFcmToken("rotated_token_${UUID.randomUUID()}")
        val secondReg = withTimeout(DEFAULT_TIMEOUT_MS) {
            DeviceRegistrationManager.registerOrSyncDevice(appContext, firestoreOverride = firestore)
        }
        assertThat(secondReg.isSuccess).isTrue()

        val deviceDoc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("devices")
                .document(DeviceRegistrationManager.getDeviceId())
                .get()
                .await()
        }
        assertThat(deviceDoc.exists()).isTrue()
        assertThat(deviceDoc.getString("ownerUid")).isEqualTo(ownerUid)

        // Sync user settings to UID-isolated path
        val settingsSync = withTimeout(DEFAULT_TIMEOUT_MS) {
            RemoteSettingsSyncManager.syncCurrentSettingsToCloud(appContext, firestoreOverride = firestore)
        }
        assertThat(settingsSync.isSuccess).isTrue()
    }

    @Test
    fun authenticatedNonAdmin_cannotCreatePendingCommandsOrModifyAdminRoles() = runBlocking {
        val ownerUid = signInTestUser(OWNER_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            DeviceRegistrationManager.registerOrSyncDevice(appContext, firestoreOverride = firestore).getOrThrow()
        }
        val deviceId = DeviceRegistrationManager.getDeviceId()
        val cmdId = "cmd_forged_${UUID.randomUUID()}"

        // Even the device owner cannot inject self-authorized remote commands into pending_commands
        val createCmdEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("devices")
                    .document(deviceId)
                    .collection("pending_commands")
                    .document(cmdId)
                    .set(
                        mapOf(
                            "commandId" to cmdId,
                            "ownerUid" to ownerUid,
                            "issuedByUid" to ownerUid,
                            "action" to "LOCKDOWN",
                            "status" to "PENDING",
                            "expiresAt" to (System.currentTimeMillis() + 60_000L),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }
        }
        assertThat(createCmdEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        // Non-admin cannot promote themselves in /admin_roles/config
        val adminWriteEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("admin_roles")
                    .document("config")
                    .set(
                        mapOf(
                            "primary_admin_uid" to ownerUid,
                            "authorized_admin_uids" to listOf(ownerUid)
                        )
                    )
                    .await()
            }
        }
        assertThat(adminWriteEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        val isAdmin = withTimeout(DEFAULT_TIMEOUT_MS) {
            OwnerSecurityBridge.isCallerAuthorizedAdmin(appContext, ownerUid, firestoreOverride = firestore)
        }
        assertThat(isAdmin).isFalse()
    }

    @Test
    fun crossUserIsolation_preventsReadingOtherUsersDevicesAndSubmittingSpoofedEvents() = runBlocking {
        val ownerUid = signInTestUser(OWNER_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            DeviceRegistrationManager.registerOrSyncDevice(appContext, firestoreOverride = firestore).getOrThrow()
        }
        val ownerDeviceId = DeviceRegistrationManager.getDeviceId()

        // Owner can record own security event cleanly
        val eventId = "evt_${UUID.randomUUID()}"
        val logged = withTimeout(DEFAULT_TIMEOUT_MS) {
            OwnerSecurityBridge.submitSecurityEvent(
                context = appContext,
                event = SecurityEvent(
                    eventId = eventId,
                    eventType = "TEST_AUDIT",
                    category = SecurityEventCategory.AUTHENTICATION,
                    severity = SecurityRiskSeverity.INFO,
                    accountIdentifier = ownerUid,
                    detectionSource = "RuleTest",
                    verificationStatus = VerificationStatus.CONFIRMED,
                    actionTaken = SecurityAction.LOGGED_ONLY,
                    details = mapOf("probe" to "ok")
                ),
                firestoreOverride = firestore
            )
        }
        assertThat(logged).isTrue()

        // Sign in as second user (Bob) -> blocked from reading Owner's device and Owner's security event
        signInTestUser(OTHER_EMAIL)

        val readDeviceEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("devices").document(ownerDeviceId).get().await()
            }
        }
        assertThat(readDeviceEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        val readEventEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("security_events").document(eventId).get().await()
            }
        }
        assertThat(readEventEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)

        // CommandSyncWorker-aligned query on Bob's UID cannot see Owner's pending commands on Owner's device
        val listPendingEx = assertThrowsType<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("devices")
                    .document(ownerDeviceId)
                    .collection("pending_commands")
                    .whereEqualTo("ownerUid", ownerUid)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .await()
            }
        }
        assertThat(listPendingEx.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    private companion object {
        const val OWNER_EMAIL = "rr_owner_test@example.com"
        const val OTHER_EMAIL = "rr_other_test@example.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
    }
}
