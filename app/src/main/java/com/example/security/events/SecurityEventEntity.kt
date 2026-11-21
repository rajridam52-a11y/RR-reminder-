package com.example.security.events

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey
    val eventId: String,
    val eventType: String,
    val category: String,
    val severity: String,
    val timestamp: Long,
    val accountIdentifier: String?,
    val detectionSource: String,
    val verificationStatus: String,
    val actionTaken: String,
    val detailsJson: String
) {
    fun toDomain(): SecurityEvent {
        val parsedDetails = mutableMapOf<String, String>()
        try {
            if (detailsJson.isNotBlank()) {
                val obj = JSONObject(detailsJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    parsedDetails[key] = obj.optString(key, "")
                }
            }
        } catch (_: Exception) {
        }

        val cat = runCatching { SecurityEventCategory.valueOf(category) }
            .getOrDefault(SecurityEventCategory.SYSTEM)
        val sev = runCatching { SecurityRiskSeverity.valueOf(severity) }
            .getOrDefault(SecurityRiskSeverity.INFO)
        val ver = runCatching { VerificationStatus.valueOf(verificationStatus) }
            .getOrDefault(VerificationStatus.CONFIRMED)
        val act = runCatching { SecurityAction.valueOf(actionTaken) }
            .getOrDefault(SecurityAction.LOGGED_ONLY)

        return SecurityEvent(
            eventId = eventId,
            eventType = eventType,
            category = cat,
            severity = sev,
            timestamp = timestamp,
            accountIdentifier = accountIdentifier,
            detectionSource = detectionSource,
            verificationStatus = ver,
            actionTaken = act,
            details = parsedDetails
        )
    }

    companion object {
        fun fromDomain(event: SecurityEvent): SecurityEventEntity {
            val obj = JSONObject()
            event.details.forEach { (k, v) -> obj.put(k, v) }
            return SecurityEventEntity(
                eventId = event.eventId,
                eventType = event.eventType,
                category = event.category.name,
                severity = event.severity.name,
                timestamp = event.timestamp,
                accountIdentifier = event.accountIdentifier,
                detectionSource = event.detectionSource,
                verificationStatus = event.verificationStatus.name,
                actionTaken = event.actionTaken.name,
                detailsJson = obj.toString()
            )
        }
    }
}
