package com.example.security.settings

data class ConfigurationAuditRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val level: SecurityLevel = SecurityLevel.STANDARD,
    val summary: String = "Default standard configuration active"
)
