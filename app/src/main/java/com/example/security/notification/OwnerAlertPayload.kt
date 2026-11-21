package com.example.security.notification

data class OwnerAlertPayload(
    val alertId: String,
    val title: String,
    val summary: String,
    val severity: String,
    val category: String,
    val eventId: String,
    val timestamp: Long,
    val isCritical: Boolean
)
