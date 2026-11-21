package com.example.security

data class IntegrityReport(
    val isCompromised: Boolean,
    val checks: List<IntegrityCheckResult>,
    val timestamp: Long = System.currentTimeMillis()
)
