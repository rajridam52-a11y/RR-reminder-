package com.example.security

data class FullSecurityAuditReport(
    val score: Int,
    val status: AuditStatus,
    val checks: List<AuditCheck>,
    val timestamp: Long = System.currentTimeMillis()
)
