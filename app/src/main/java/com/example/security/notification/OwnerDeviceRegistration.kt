package com.example.security.notification

data class OwnerDeviceRegistration(
    val deviceId: String,
    val deviceModel: String,
    val fcmToken: String,
    val registeredAt: Long,
    val lastSeenAt: Long,
    val isActive: Boolean
)
