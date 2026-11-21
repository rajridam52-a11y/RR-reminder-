package com.example.integration.protection

data class RegisteredDeviceInfo(
    val deviceId: String,
    val deviceModel: String,
    val registeredAt: Long,
    val lastActiveAt: Long,
    val authStatus: DeviceAuthStatus
)
