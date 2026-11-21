package com.example.integration.protection

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.dao.ProcessedCommandDao
import com.example.data.model.ProcessedCommandEntity
import com.example.integration.firebase.FirestoreProvider
import com.example.integration.firebase.OperationType
import com.example.integration.firebase.handleFirestoreError
import com.example.security.events.SecurityAction
import com.example.security.events.SecurityEvent
import com.example.security.events.SecurityEventCategory
import com.example.security.events.SecurityEventManager
import com.example.security.events.SecurityRiskSeverity
import com.example.security.events.VerificationStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object CommandProcessor {
    private const val TAG = "CommandProcessor"
    private const val PRUNE_RETENTION_MS = 30L * 24L * 60L * 60L * 1000L

    private val ALLOWED_ACTIONS = setOf("LOCKDOWN", "RELEASE_LOCKDOWN", "SYNC_SETTINGS", "PING")

    suspend fun processCommand(
        context: Context,
        commandId: String,
        data: Map<String, Any?>,
        firestoreOverride: FirebaseFirestore? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (commandId.isBlank() || commandId.length > 128) {
            Log.w(TAG, "Rejected command with invalid ID")
            return@withContext false
        }

        val db = AppDatabase.getDatabase(context.applicationContext)
        val dao = db.processedCommandDao()

        // 1. Anti-Replay / Deduplication Check in local Room SQLite
        if (dao.isCommandProcessed(commandId)) {
            Log.d(TAG, "Command $commandId already processed. Skipping duplicate replay.")
            return@withContext true
        }

        val action = ((data["action"] as? String) ?: (data["type"] as? String) ?: "UNKNOWN").uppercase()

        // 2. Expiration Check
        val expiresAtMillis = when (val exp = data["expiresAt"]) {
            is Number -> exp.toLong()
            is Timestamp -> exp.toDate().time
            is String -> exp.toLongOrNull() ?: Long.MAX_VALUE
            else -> Long.MAX_VALUE
        }
        val now = System.currentTimeMillis()
        if (now > expiresAtMillis) {
            Log.w(TAG, "Command $commandId expired at $expiresAtMillis (now: $now). Rejecting.")
            SecurityEventManager.recordEvent(
                SecurityEvent(
                    eventType = SecurityEvent.TYPE_REMOTE_COMMAND_REJECTED,
                    category = SecurityEventCategory.SYSTEM,
                    severity = SecurityRiskSeverity.MEDIUM,
                    detectionSource = "CommandProcessor",
                    verificationStatus = VerificationStatus.CONFIRMED,
                    actionTaken = SecurityAction.BLOCKED,
                    details = mapOf("commandId" to commandId, "action" to action, "reason" to "EXPIRED")
                )
            )
            recordAndAcknowledge(
                context = context,
                dao = dao,
                commandId = commandId,
                action = action,
                localStatus = "REJECTED_EXPIRED",
                remoteStatus = "EXPIRED",
                error = "Command expired before processing",
                firestoreOverride = firestoreOverride
            )
            return@withContext false
        }

        // 3. Action Allowlist Check
        if (action !in ALLOWED_ACTIONS) {
            Log.w(TAG, "Rejected unknown remote command action: $action")
            recordAndAcknowledge(
                context = context,
                dao = dao,
                commandId = commandId,
                action = action,
                localStatus = "REJECTED_INVALID",
                remoteStatus = "FAILED",
                error = "Unsupported command action: $action",
                firestoreOverride = firestoreOverride
            )
            return@withContext false
        }

        // 4. Execute Validated Command (Never touches user reminders, alarms, notes, or planner tasks)
        Log.i(TAG, "Executing authorized remote command: $commandId, action: $action")
        val executed = when (action) {
            "LOCKDOWN" -> {
                val reason = (data["reason"] as? String)?.take(240)
                    ?: "Remote administrator triggered emergency lockdown"
                EmergencyLockdownManager.triggerLockdown(reason)
                true
            }
            "RELEASE_LOCKDOWN" -> {
                val key = (data["pin"] as? String) ?: ""
                EmergencyLockdownManager.releaseLockdown(key, isRemoteAdminAuthorized = true)
            }
            "SYNC_SETTINGS" -> {
                val secLevelStr = ((data["securityLevel"] as? String) ?: "STANDARD").uppercase()
                val parsedLevel = try {
                    com.example.security.settings.SecurityLevel.valueOf(secLevelStr)
                } catch (_: Exception) {
                    com.example.security.settings.SecurityLevel.STANDARD
                }
                val defaultMaxFails = when (parsedLevel) {
                    com.example.security.settings.SecurityLevel.STANDARD -> 5
                    com.example.security.settings.SecurityLevel.HIGH -> 3
                    com.example.security.settings.SecurityLevel.MAXIMUM -> 2
                }
                val defaultLockoutSec = when (parsedLevel) {
                    com.example.security.settings.SecurityLevel.STANDARD -> 60L
                    com.example.security.settings.SecurityLevel.HIGH -> 120L
                    com.example.security.settings.SecurityLevel.MAXIMUM -> 300L
                }
                val maxFails = ((data["maxFailures"] as? Number)?.toInt()
                    ?: (data["maxFailures"] as? String)?.toIntOrNull()
                    ?: defaultMaxFails).coerceIn(2, 10)
                val lockoutSec = ((data["lockoutSeconds"] as? Number)?.toLong()
                    ?: (data["lockoutSeconds"] as? String)?.toLongOrNull()
                    ?: defaultLockoutSec).coerceIn(30L, 600L)
                val autoLockdown = when (val v = data["autoLockdownOnTamper"]) {
                    is Boolean -> v
                    is String -> v.toBooleanStrictOrNull() ?: true
                    else -> true
                }
                RemoteSettingsSyncManager.applyRemoteSettings(
                    RemoteSyncableSettings(
                        securityLevel = parsedLevel.name,
                        maxFailures = maxFails,
                        lockoutSeconds = lockoutSec,
                        autoLockdownOnTamper = autoLockdown
                    )
                )
                true
            }
            "PING" -> {
                Log.d(TAG, "Inbound administrator ping acknowledged")
                true
            }
            else -> false
        }

        SecurityEventManager.recordEvent(
            SecurityEvent(
                eventType = SecurityEvent.TYPE_REMOTE_COMMAND_EXECUTED,
                category = SecurityEventCategory.ADMIN_PRIVILEGE,
                severity = if (action == "LOCKDOWN") SecurityRiskSeverity.HIGH else SecurityRiskSeverity.INFO,
                detectionSource = "CommandProcessor",
                verificationStatus = VerificationStatus.CONFIRMED,
                actionTaken = SecurityAction.LOGGED_ONLY,
                details = mapOf("commandId" to commandId, "action" to action, "executed" to executed.toString())
            )
        )

        recordAndAcknowledge(
            context = context,
            dao = dao,
            commandId = commandId,
            action = action,
            localStatus = if (executed) "APPLIED" else "REJECTED_INVALID",
            remoteStatus = if (executed) "COMPLETED" else "FAILED",
            error = if (executed) null else "Command execution declined",
            firestoreOverride = firestoreOverride
        )

        // Opportunistically prune stale deduplication records older than 30 days
        try {
            dao.pruneOldCommands(now - PRUNE_RETENTION_MS)
        } catch (_: Exception) {
        }

        executed
    }

    private suspend fun recordAndAcknowledge(
        context: Context,
        dao: ProcessedCommandDao,
        commandId: String,
        action: String,
        localStatus: String,
        remoteStatus: String,
        error: String?,
        firestoreOverride: FirebaseFirestore?
    ) {
        // Persist in Room SQLite immediately to prevent duplicate replay even offline
        dao.recordProcessedCommand(
            ProcessedCommandEntity(
                commandId = commandId,
                commandType = action,
                processedAtMillis = System.currentTimeMillis(),
                status = localStatus
            )
        )

        // Acknowledge back to Firestore only when authenticated
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser == null) {
            Log.d(TAG, "Skipping remote command acknowledgment while signed out")
            return
        }

        val deviceId = DeviceRegistrationManager.getDeviceId()
        if (deviceId.isBlank()) return

        val firestore = firestoreOverride ?: FirestoreProvider.getFirestoreOrNull(context) ?: return
        val docRef = firestore.collection("devices")
            .document(deviceId)
            .collection("pending_commands")
            .document(commandId)

        val updatePayload = mutableMapOf<String, Any>(
            "status" to remoteStatus,
            "acknowledgedAt" to FieldValue.serverTimestamp()
        )
        if (!error.isNullOrBlank()) {
            updatePayload["error"] = error
        }

        try {
            docRef.update(updatePayload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Log.d(TAG, "Could not acknowledge command status to Firestore (offline or terminal): ${e.message}")
        }
    }
}
