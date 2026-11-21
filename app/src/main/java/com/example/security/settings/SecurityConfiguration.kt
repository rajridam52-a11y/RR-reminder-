package com.example.security.settings

data class SecurityConfiguration(
    val level: SecurityLevel = SecurityLevel.STANDARD,
    val maxConsecutiveFailures: Int = 5,
    val lockoutDurationSeconds: Long = 60,
    val minAlertSeverity: String = "HIGH",
    val requireBiometricsForSensitiveOps: Boolean = false,
    val remoteLockdownEnabled: Boolean = true
)
