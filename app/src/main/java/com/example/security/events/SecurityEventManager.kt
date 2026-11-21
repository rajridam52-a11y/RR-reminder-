package com.example.security.events

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.AppDatabase
import com.example.security.notification.OwnerNotificationManager
import com.example.security.settings.SecuritySettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object SecurityEventManager {
    private const val TAG = "SecurityEventManager"
    private const val MAX_LOCAL_EVENTS = 500
    private const val LOCKOUT_DURATION_MS = 60000L
    private const val FAILURE_WINDOW_MS = 900000L

    private val scope = CoroutineScope(Dispatchers.IO)
    private var appContext: Context? = null
    private var database: AppDatabase? = null

    private val consecutiveFailures = AtomicInteger(0)
    private val lastFailureTimestamp = AtomicLong(0L)
    private val lockoutUntilTimestamp = AtomicLong(0L)
    private val memoryJournal = ConcurrentHashMap<String, SecurityEvent>()

    private val _eventStream = MutableSharedFlow<SecurityEvent>(replay = 20)
    val eventStream: SharedFlow<SecurityEvent> = _eventStream.asSharedFlow()

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            database = AppDatabase.getDatabase(context.applicationContext)
            Log.d(TAG, "Security event monitoring initialized")
        }
    }

    fun recordEvent(event: SecurityEvent) {
        val enriched = enrichWithDeviceInfo(event)
        memoryJournal[enriched.eventId] = enriched

        if (memoryJournal.size > 100) {
            val oldest = memoryJournal.values.minByOrNull { it.timestamp }
            if (oldest != null) {
                memoryJournal.remove(oldest.eventId)
            }
        }

        scope.launch {
            try {
                database?.securityEventDao()?.insertEvent(SecurityEventEntity.fromDomain(enriched))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist security event locally", e)
            }
        }

        if (enriched.severity != SecurityRiskSeverity.INFO) {
            OwnerNotificationManager.stageOwnerAlert(enriched)
        }

        scope.launch {
            _eventStream.emit(enriched)
        }
    }

    fun reportFailedLoginAttempt(source: String, attemptedIdentifier: String): SecurityAction {
        val now = System.currentTimeMillis()
        val lastFail = lastFailureTimestamp.get()
        if (now - lastFail > FAILURE_WINDOW_MS) {
            consecutiveFailures.set(0)
        }
        val count = consecutiveFailures.incrementAndGet()
        lastFailureTimestamp.set(now)

        val secConfig = SecuritySettingsManager.getConfiguration()
        val lockoutThreshold = secConfig.maxConsecutiveFailures
        val lockoutDurationMs = secConfig.lockoutDurationSeconds * 1000L

        val action: SecurityAction
        val severity: SecurityRiskSeverity
        if (count < lockoutThreshold) {
            if (count >= (lockoutThreshold - 2).coerceAtLeast(2)) {
                severity = SecurityRiskSeverity.HIGH
                action = SecurityAction.THROTTLED
            } else {
                severity = SecurityRiskSeverity.MEDIUM
                action = SecurityAction.LOGGED_ONLY
            }
        } else {
            severity = SecurityRiskSeverity.CRITICAL
            action = SecurityAction.BLOCKED
            lockoutUntilTimestamp.set(now + lockoutDurationMs)
        }

        val details = mapOf(
            "consecutiveFailures" to count.toString(),
            "windowMinutes" to "15",
            "lockoutActive" to (now < lockoutUntilTimestamp.get()).toString()
        )

        recordEvent(
            SecurityEvent(
                eventType = SecurityEvent.TYPE_AUTH_FAILED_REPEATED,
                category = SecurityEventCategory.AUTHENTICATION,
                severity = severity,
                accountIdentifier = anonymizeIdentifier(attemptedIdentifier),
                detectionSource = source,
                verificationStatus = VerificationStatus.CONFIRMED,
                actionTaken = action,
                details = details
            )
        )
        return action
    }

    fun reportSuccessfulLogin(source: String, identifier: String) {
        val previousFailures = consecutiveFailures.getAndSet(0)
        lockoutUntilTimestamp.set(0L)
        recordEvent(
            SecurityEvent(
                eventType = "AUTH_SUCCESS",
                category = SecurityEventCategory.AUTHENTICATION,
                severity = SecurityRiskSeverity.INFO,
                accountIdentifier = anonymizeIdentifier(identifier),
                detectionSource = source,
                verificationStatus = VerificationStatus.CONFIRMED,
                actionTaken = SecurityAction.LOGGED_ONLY,
                details = mapOf("clearedFailures" to previousFailures.toString())
            )
        )
    }

    fun isTemporaryLockoutActive(): Boolean {
        return System.currentTimeMillis() < lockoutUntilTimestamp.get()
    }

    fun getRemainingLockoutSeconds(): Long {
        val remaining = lockoutUntilTimestamp.get() - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000 else 0L
    }

    fun reportTamperingDetected(component: String, indicator: String, isConfirmed: Boolean) {
        val severity = if (isConfirmed) SecurityRiskSeverity.CRITICAL else SecurityRiskSeverity.HIGH
        val action = if (isConfirmed) SecurityAction.BLOCKED else SecurityAction.LOGGED_ONLY
        recordEvent(
            SecurityEvent(
                eventType = SecurityEvent.TYPE_APPLICATION_TAMPERING_DETECTED,
                category = SecurityEventCategory.SYSTEM_ENVIRONMENT,
                severity = severity,
                detectionSource = component,
                verificationStatus = if (isConfirmed) VerificationStatus.CONFIRMED else VerificationStatus.SUSPICIOUS,
                actionTaken = action,
                details = mapOf("indicator" to indicator, "confirmed" to isConfirmed.toString())
            )
        )
    }

    fun reportUnauthorizedOwnerAccess(attemptedResource: String, callerUid: String?) {
        recordEvent(
            SecurityEvent(
                eventType = SecurityEvent.TYPE_UNAUTHORIZED_OWNER_ACCESS,
                category = SecurityEventCategory.ADMIN_PRIVILEGE,
                severity = SecurityRiskSeverity.CRITICAL,
                accountIdentifier = callerUid ?: "unauthenticated",
                detectionSource = "OwnerSecurityBridge",
                verificationStatus = VerificationStatus.CONFIRMED,
                actionTaken = SecurityAction.BLOCKED,
                details = mapOf("resource" to attemptedResource)
            )
        )
    }

    fun reportSuspiciousApiRequest(endpoint: String, reason: String) {
        recordEvent(
            SecurityEvent(
                eventType = SecurityEvent.TYPE_SUSPICIOUS_API_REQUEST,
                category = SecurityEventCategory.NETWORK_TRANSMISSION,
                severity = SecurityRiskSeverity.HIGH,
                detectionSource = endpoint,
                verificationStatus = VerificationStatus.SUSPICIOUS,
                actionTaken = SecurityAction.THROTTLED,
                details = mapOf("reason" to reason)
            )
        )
    }

    private fun enrichWithDeviceInfo(event: SecurityEvent): SecurityEvent {
        val deviceMap = mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "sdk" to Build.VERSION.SDK_INT.toString(),
            "brand" to Build.BRAND
        )
        return event.copy(deviceInfo = deviceMap)
    }

    private fun anonymizeIdentifier(id: String): String {
        if (id.isBlank() || id == "anonymous") return "anonymous"
        if (id.length <= 4) return "****"
        return id.take(2) + "***" + id.takeLast(2)
    }

    suspend fun getRecentEvents(limit: Int = 50): List<SecurityEvent> {
        return try {
            database?.securityEventDao()?.getRecentEvents(limit)?.map { it.toDomain() }
                ?: memoryJournal.values.sortedByDescending { it.timestamp }.take(limit)
        } catch (e: Exception) {
            memoryJournal.values.sortedByDescending { it.timestamp }.take(limit)
        }
    }
}
