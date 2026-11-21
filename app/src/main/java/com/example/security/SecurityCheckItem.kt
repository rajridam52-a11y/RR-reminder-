package com.example.security

data class SecurityCheckItem(
    val name: String,
    val status: String,
    val details: String,
    val isCritical: Boolean = false
)
