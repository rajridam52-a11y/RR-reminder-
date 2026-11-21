package com.example.security

object InputValidator {
    fun isValidPassword(password: String): Boolean {
        return password.length in 4..64
    }

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun sanitizeText(input: String): String {
        return input.replace("<", "&lt;").replace(">", "&gt;").trim()
    }
}
