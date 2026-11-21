package com.example.security.backend

data class OwnerStatusSummary(
    val isOwnerVerified: Boolean,
    val activeDevicesCount: Int,
    val lastSyncTimestamp: Long,
    val securityLevel: String,
    val systemStatus: String
)
