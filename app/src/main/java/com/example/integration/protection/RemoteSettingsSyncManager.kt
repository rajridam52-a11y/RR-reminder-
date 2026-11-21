package com.example.integration.protection

import android.content.Context
import android.util.Log
import com.example.integration.firebase.FirestoreProvider
import com.example.integration.firebase.OperationType
import com.example.integration.firebase.handleFirestoreError
import com.example.security.settings.SecurityLevel
import com.example.security.settings.SecuritySettingsManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object RemoteSettingsSyncManager {
    private const val TAG = "RemoteSettingsSync"

    fun initialize(context: Context) {
        // SecuritySettingsManager handles loading local preferences safely
    }

    fun applyRemoteSettings(settings: RemoteSyncableSettings) {
        val current = SecuritySettingsManager.getConfiguration()
        val level = try {
            SecurityLevel.valueOf(settings.securityLevel.uppercase())
        } catch (_: Exception) {
            current.level
        }
        val minAlertSeverity = when (level) {
            SecurityLevel.STANDARD -> "HIGH"
            SecurityLevel.HIGH, SecurityLevel.MAXIMUM -> "MEDIUM"
        }
        val requireBiometrics = when (level) {
            SecurityLevel.STANDARD -> false
            SecurityLevel.HIGH, SecurityLevel.MAXIMUM -> true
        }
        val updated = current.copy(
            level = level,
            maxConsecutiveFailures = settings.maxFailures.coerceIn(2, 10),
            lockoutDurationSeconds = settings.lockoutSeconds.coerceIn(30L, 600L),
            minAlertSeverity = minAlertSeverity,
            requireBiometricsForSensitiveOps = requireBiometrics,
            remoteLockdownEnabled = settings.autoLockdownOnTamper
        )
        SecuritySettingsManager.updateConfiguration(updated)
        Log.i(TAG, "Applied remote security settings: $updated")
    }

    fun getCurrentSettings(): RemoteSyncableSettings {
        val config = SecuritySettingsManager.getConfiguration()
        return RemoteSyncableSettings(
            securityLevel = config.level.name,
            maxFailures = config.maxConsecutiveFailures,
            lockoutSeconds = config.lockoutDurationSeconds,
            notificationsEnabled = true,
            autoLockdownOnTamper = config.remoteLockdownEnabled
        )
    }

    suspend fun syncCurrentSettingsToCloud(
        context: Context,
        firestoreOverride: com.google.firebase.firestore.FirebaseFirestore? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val user = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        } ?: return@withContext Result.failure(IllegalStateException("Google Sign-In required"))

        val firestore = firestoreOverride ?: FirestoreProvider.getFirestoreOrNull(context)
            ?: return@withContext Result.failure(IllegalStateException("Firestore unavailable"))

        val current = getCurrentSettings()
        val docRef = firestore.collection("users")
            .document(user.uid)
            .collection("settings")
            .document("security_profile")

        val payload = hashMapOf<String, Any>(
            "securityLevel" to current.securityLevel,
            "maxFailures" to current.maxFailures,
            "lockoutSeconds" to current.lockoutSeconds,
            "notificationsEnabled" to current.notificationsEnabled,
            "autoLockdownOnTamper" to current.autoLockdownOnTamper,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
