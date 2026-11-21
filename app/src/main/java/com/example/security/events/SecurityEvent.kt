package com.example.security.events

import java.util.UUID

enum class SecurityEventCategory {
    AUTHENTICATION,
    INTEGRITY,
    SYSTEM,
    SYSTEM_ENVIRONMENT,
    DATA_PROTECTION,
    NETWORK,
    NETWORK_TRANSMISSION,
    ADMIN_PRIVILEGE
}

enum class SecurityRiskSeverity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class VerificationStatus {
    UNVERIFIED,
    HEURISTIC_MATCH,
    SUSPICIOUS,
    CONFIRMED,
    DISMISSED_FALSE_POSITIVE
}

enum class SecurityAction {
    LOGGED_ONLY,
    THROTTLED,
    CHALLENGED,
    CHALLENGE_REQUIRED,
    ISOLATED,
    BLOCKED
}

data class SecurityEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val category: SecurityEventCategory,
    val severity: SecurityRiskSeverity,
    val timestamp: Long = System.currentTimeMillis(),
    val accountIdentifier: String? = null,
    val detectionSource: String = "InternalMonitor",
    val verificationStatus: VerificationStatus = VerificationStatus.CONFIRMED,
    val actionTaken: SecurityAction = SecurityAction.LOGGED_ONLY,
    val details: Map<String, String> = emptyMap(),
    val deviceInfo: Map<String, String> = emptyMap()
) {
    companion object {
        const val TYPE_AUTH_FAILED_REPEATED = "AUTH_FAILED_REPEATED"
        const val TYPE_APPLICATION_TAMPERING_DETECTED = "APPLICATION_TAMPERING_DETECTED"
        const val TYPE_UNAUTHORIZED_OWNER_ACCESS = "UNAUTHORIZED_OWNER_ACCESS"
        const val TYPE_PRIVILEGE_ESCALATION_ATTEMPT = "PRIVILEGE_ESCALATION_ATTEMPT"
        const val TYPE_SUSPICIOUS_API_REQUEST = "SUSPICIOUS_API_REQUEST"
        const val TYPE_REMOTE_COMMAND_EXECUTED = "REMOTE_COMMAND_EXECUTED"
        const val TYPE_REMOTE_COMMAND_REJECTED = "REMOTE_COMMAND_REJECTED"
    }
}
