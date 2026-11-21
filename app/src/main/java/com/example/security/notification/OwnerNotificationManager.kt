package com.example.security.notification

import android.util.Log
import com.example.security.events.SecurityEvent
import com.example.security.events.SecurityRiskSeverity
import com.example.security.settings.SecuritySettingsManager
import java.util.concurrent.ConcurrentHashMap

object OwnerNotificationManager {
    private const val TAG = "OwnerNotification"
    private const val DEDUPLICATION_WINDOW_MS = 300000L
    private val lastAlertTimestamps = ConcurrentHashMap<String, Long>()
    private val registeredOwnerDevices = ConcurrentHashMap<String, OwnerDeviceRegistration>()
    private val stagedAlertsQueue = ConcurrentHashMap<String, OwnerAlertPayload>()

    fun registerOwnerDevice(registration: OwnerDeviceRegistration) {
        registeredOwnerDevices[registration.deviceId] = registration
        Log.i(TAG, "Owner device registered: ${registration.deviceModel} (Active=${registration.isActive})")
    }

    fun revokeOwnerDevice(deviceId: String) {
        val existing = registeredOwnerDevices[deviceId]
        if (existing != null) {
            registeredOwnerDevices[deviceId] = existing.copy(isActive = false)
            Log.i(TAG, "Owner device revoked: $deviceId")
        }
    }

    fun stageOwnerAlert(event: SecurityEvent): OwnerAlertPayload? {
        val minSeverity = try {
            SecuritySettingsManager.getConfiguration().minAlertSeverity
        } catch (_: Exception) {
            "HIGH"
        }
        val qualifies = when (minSeverity) {
            "MEDIUM" -> event.severity == SecurityRiskSeverity.CRITICAL ||
                event.severity == SecurityRiskSeverity.HIGH ||
                event.severity == SecurityRiskSeverity.MEDIUM
            "CRITICAL" -> event.severity == SecurityRiskSeverity.CRITICAL
            else -> event.severity == SecurityRiskSeverity.CRITICAL ||
                event.severity == SecurityRiskSeverity.HIGH
        }
        if (!qualifies) return null

        val now = System.currentTimeMillis()
        val lastSent = lastAlertTimestamps[event.eventType] ?: 0L
        if (now - lastSent < DEDUPLICATION_WINDOW_MS) {
            Log.d(TAG, "Alert for ${event.eventType} suppressed by deduplication cooldown")
            return null
        }
        lastAlertTimestamps[event.eventType] = now

        val sanitizedTitle = when (event.severity) {
            SecurityRiskSeverity.CRITICAL -> "Security Alert [CRITICAL]"
            SecurityRiskSeverity.HIGH -> "Security Notice [HIGH]"
            else -> "Security Notice"
        }

        val sanitizedSummary = when (event.eventType) {
            SecurityEvent.TYPE_UNAUTHORIZED_OWNER_ACCESS -> "Unauthorized access attempt to owner management endpoints was blocked."
            SecurityEvent.TYPE_PRIVILEGE_ESCALATION_ATTEMPT -> "Privilege escalation attempt intercepted."
            SecurityEvent.TYPE_APPLICATION_TAMPERING_DETECTED -> "Application integrity anomaly detected by environment audit."
            SecurityEvent.TYPE_SUSPICIOUS_API_REQUEST -> "Rate limit violation or malformed payload blocked by security filter."
            SecurityEvent.TYPE_AUTH_FAILED_REPEATED -> "Multiple consecutive failed authentication attempts detected on client."
            else -> "Security event ${event.eventType} recorded by client protection layer."
        }

        val payload = OwnerAlertPayload(
            alertId = "alert_${event.eventId.take(8)}",
            title = sanitizedTitle,
            summary = sanitizedSummary,
            severity = event.severity.name,
            category = event.category.name,
            eventId = event.eventId,
            timestamp = event.timestamp,
            isCritical = event.severity == SecurityRiskSeverity.CRITICAL
        )
        stagedAlertsQueue[payload.alertId] = payload
        Log.i(TAG, "Owner alert staged: ${payload.title} - ${payload.summary}")
        return payload
    }

    fun getStagedAlerts(): List<OwnerAlertPayload> {
        return stagedAlertsQueue.values.sortedByDescending { it.timestamp }
    }

    fun clearStagedAlert(alertId: String) {
        stagedAlertsQueue.remove(alertId)
    }

    fun getActiveOwnerDeviceCount(): Int {
        return registeredOwnerDevices.values.count { it.isActive }
    }
}
