package com.example.security.backend

import android.content.Context
import android.util.Log
import com.example.integration.firebase.FirestoreProvider
import com.example.integration.firebase.OperationType
import com.example.integration.firebase.handleFirestoreError
import com.example.integration.protection.DeviceRegistrationManager
import com.example.integration.protection.EmergencyLockdownManager
import com.example.integration.protection.RegisteredDeviceInfo
import com.example.integration.protection.RemoteSettingsSyncManager
import com.example.integration.protection.RemoteSyncableSettings
import com.example.security.events.SecurityEvent
import com.example.security.events.SecurityEventManager
import com.example.security.notification.OwnerDeviceRegistration
import com.example.security.notification.OwnerNotificationManager
import com.example.security.settings.ConfigurationAuditRecord
import com.example.security.settings.SecurityConfiguration
import com.example.security.settings.SecurityLevel
import com.example.security.settings.SecuritySettingsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object OwnerSecurityBridge {
    private const val TAG = "OwnerSecurityBridge"
    private val revokedSessionIds = ConcurrentHashMap.newKeySet<String>()

    fun verifyOwnerToken(authToken: String?): BackendAuthResult {
        if (authToken.isNullOrBlank()) {
            SecurityEventManager.reportUnauthorizedOwnerAccess("verifyOwnerToken", null)
            return BackendAuthResult(false, "DENIED", "Authentication token missing")
        }
        if (revokedSessionIds.contains(authToken)) {
            SecurityEventManager.reportUnauthorizedOwnerAccess("verifyOwnerToken_revoked", "revoked_session")
            return BackendAuthResult(false, "DENIED", "Session has been revoked")
        }

        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser != null && authToken == currentUser.uid) {
            return BackendAuthResult(true, "OWNER", "Verified Firebase UID credentials")
        }

        SecurityEventManager.reportUnauthorizedOwnerAccess("verifyOwnerToken_unverified", authToken.take(8))
        return BackendAuthResult(false, "DENIED", "Unverified owner credentials; sign in with Google")
    }

    suspend fun isCallerAuthorizedAdmin(
        context: Context,
        callerUid: String,
        firestoreOverride: FirebaseFirestore? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser == null || currentUser.uid != callerUid) {
            return@withContext false
        }

        val firestore = firestoreOverride ?: FirestoreProvider.getFirestoreOrNull(context)
            ?: return@withContext false

        val path = "admin_roles/config"
        try {
            val doc = firestore.collection("admin_roles").document("config").get().await()
            if (!doc.exists()) return@withContext false
            val authorizedUids = doc.get("authorized_admin_uids") as? List<*>
            if (authorizedUids?.contains(callerUid) == true) {
                return@withContext true
            }
            val legacyAdminUids = doc.get("admin_uids") as? List<*>
            if (legacyAdminUids?.contains(callerUid) == true) {
                return@withContext true
            }
            doc.getString("primary_admin_uid") == callerUid
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Log.w(TAG, "Admin verification declined or offline: ${e.message}")
            false
        }
    }

    suspend fun submitSecurityEvent(
        context: Context,
        event: SecurityEvent,
        firestoreOverride: FirebaseFirestore? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        } ?: return@withContext false

        val firestore = firestoreOverride ?: FirestoreProvider.getFirestoreOrNull(context)
            ?: return@withContext false

        val docRef = firestore.collection("security_events").document(event.eventId)
        val data = hashMapOf<String, Any>(
            "eventId" to event.eventId,
            "userId" to currentUser.uid,
            "deviceId" to DeviceRegistrationManager.getDeviceId(),
            "eventType" to event.eventType,
            "category" to event.category.name,
            "severity" to event.severity.name,
            "timestamp" to FieldValue.serverTimestamp(),
            "accountIdentifier" to (event.accountIdentifier ?: currentUser.uid),
            "detectionSource" to event.detectionSource,
            "actionTaken" to event.actionTaken.name,
            "details" to event.details
        )
        try {
            docRef.set(data).await()
            true
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Log.w(TAG, "Failed to submit security event to Firestore: ${e.message}")
            false
        }
    }

    suspend fun getSecurityAuditHistory(ownerAuthToken: String, limit: Int = 100): Result<List<SecurityEvent>> =
        withContext(Dispatchers.IO) {
            val auth = verifyOwnerToken(ownerAuthToken)
            if (!auth.isAuthorized) {
                return@withContext Result.failure(SecurityException("Unauthorized owner access"))
            }
            val local = SecurityEventManager.getRecentEvents(limit)
            Result.success(local)
        }

    suspend fun getSecurityStatusSummary(ownerAuthToken: String): Result<OwnerStatusSummary> =
        withContext(Dispatchers.IO) {
            val auth = verifyOwnerToken(ownerAuthToken)
            if (!auth.isAuthorized) {
                return@withContext Result.failure(SecurityException("Unauthorized owner access"))
            }
            val config = SecuritySettingsManager.getConfiguration()
            val summary = OwnerStatusSummary(
                isOwnerVerified = true,
                activeDevicesCount = OwnerNotificationManager.getActiveOwnerDeviceCount(),
                lastSyncTimestamp = System.currentTimeMillis(),
                securityLevel = config.level.name,
                systemStatus = if (EmergencyLockdownManager.isLockdownActive()) "LOCKDOWN" else "NORMAL"
            )
            Result.success(summary)
        }

    suspend fun registerOwnerDevice(
        context: Context,
        ownerAuthToken: String,
        registration: OwnerDeviceRegistration,
        firestoreOverride: FirebaseFirestore? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val auth = verifyOwnerToken(ownerAuthToken)
        if (!auth.isAuthorized) {
            return@withContext Result.failure(SecurityException("Unauthorized owner access"))
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
            ?: return@withContext Result.failure(SecurityException("Google Sign-In required"))

        OwnerNotificationManager.registerOwnerDevice(registration)
        val firestore = firestoreOverride ?: FirestoreProvider.getFirestoreOrNull(context)
        if (firestore != null) {
            val docRef = firestore.collection("devices").document(registration.deviceId)
            try {
                val existing = docRef.get().await()
                val payload = hashMapOf<String, Any>(
                    "deviceId" to registration.deviceId,
                    "ownerUid" to currentUser.uid,
                    "deviceModel" to registration.deviceModel.take(128).ifBlank { "Android Device" },
                    "fcmToken" to registration.fcmToken.take(512),
                    "status" to if (registration.isActive) "ACTIVE" else "REVOKED",
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
                if (!existing.exists()) {
                    payload["registeredAt"] = FieldValue.serverTimestamp()
                    docRef.set(payload).await()
                } else {
                    docRef.update(payload).await()
                }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
                return@withContext Result.failure(e)
            }
        }
        Result.success(true)
    }

    fun revokeSession(sessionIdToRevoke: String) {
        revokedSessionIds.add(sessionIdToRevoke)
    }

    fun getCurrentSecurityLevel(ownerAuthToken: String): Result<SecurityLevel> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            Result.success(SecuritySettingsManager.getConfiguration().level)
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun getSupportedSecuritySettings(ownerAuthToken: String): Result<Map<String, Any>> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            Result.success(
                mapOf(
                    "supportedLevels" to SecurityLevel.entries.map { it.name },
                    "maxFailuresRange" to (2..10).toList(),
                    "lockoutRangeSeconds" to listOf(30, 60, 120, 300)
                )
            )
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun updateSecurityLevel(ownerAuthToken: String, newLevel: SecurityLevel): Result<SecurityConfiguration> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            SecuritySettingsManager.setSecurityLevel(newLevel)
            Result.success(SecuritySettingsManager.getConfiguration())
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun updateSecuritySettings(ownerAuthToken: String, newSettings: SecurityConfiguration): Result<SecurityConfiguration> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            SecuritySettingsManager.updateConfiguration(newSettings)
            Result.success(SecuritySettingsManager.getConfiguration())
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun getAuditReports(ownerAuthToken: String): Result<List<ConfigurationAuditRecord>> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            Result.success(
                listOf(
                    ConfigurationAuditRecord(
                        timestamp = System.currentTimeMillis(),
                        level = SecuritySettingsManager.getConfiguration().level,
                        summary = "Configuration verified against Firebase UID owner policy"
                    )
                )
            )
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun getDeviceRegistrationInfo(ownerAuthToken: String): Result<RegisteredDeviceInfo> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            Result.success(DeviceRegistrationManager.getDeviceInfo())
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun triggerEmergencyLockdown(ownerAuthToken: String, reason: String): Result<Boolean> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            EmergencyLockdownManager.triggerLockdown(reason)
            Result.success(true)
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun releaseEmergencyLockdown(ownerAuthToken: String, unlockPinOrKey: String): Result<Boolean> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            val released = EmergencyLockdownManager.releaseLockdown(unlockPinOrKey)
            Result.success(released)
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun syncRemoteSettings(ownerAuthToken: String, incomingSettings: RemoteSyncableSettings): Result<RemoteSyncableSettings> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            RemoteSettingsSyncManager.applyRemoteSettings(incomingSettings)
            Result.success(incomingSettings)
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }

    fun getRemoteSettings(ownerAuthToken: String): Result<RemoteSyncableSettings> {
        val auth = verifyOwnerToken(ownerAuthToken)
        return if (auth.isAuthorized) {
            Result.success(RemoteSettingsSyncManager.getCurrentSettings())
        } else {
            Result.failure(SecurityException("Unauthorized"))
        }
    }
}
