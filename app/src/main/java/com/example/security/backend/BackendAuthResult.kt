package com.example.security.backend

data class BackendAuthResult(
    val isAuthorized: Boolean,
    val role: String,
    val message: String
)
