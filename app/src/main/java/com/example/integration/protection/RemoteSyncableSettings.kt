package com.example.integration.protection

data class RemoteSyncableSettings(
    val securityLevel: String = "STANDARD",
    val maxFailures: Int = 5,
    val lockoutSeconds: Long = 60,
    val notificationsEnabled: Boolean = true,
    val autoLockdownOnTamper: Boolean = true
)
