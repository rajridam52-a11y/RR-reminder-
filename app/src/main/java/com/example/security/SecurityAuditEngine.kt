package com.example.security

import android.content.Context
import com.example.security.biometric.BiometricAuthManager
import com.example.security.biometric.BiometricStatus

class SecurityAuditEngine(private val context: Context) {
    private val integrityManager = IntegrityManager(context)
    private val biometricAuthManager = BiometricAuthManager(context)

    fun runFullAudit(): FullSecurityAuditReport {
        val checks = mutableListOf<AuditCheck>()
        var score = 100

        val integrity = integrityManager.performIntegrityAudit()
        if (integrity.isCompromised) {
            score -= 40
            checks.add(AuditCheck("System Integrity", "Potential system modification detected", AuditStatus.CRITICAL))
        } else {
            checks.add(AuditCheck("System Integrity", "Operating system integrity verified", AuditStatus.SECURE))
        }

        val bio = biometricAuthManager.checkBiometricAvailability()
        if (bio == BiometricStatus.AVAILABLE) {
            checks.add(AuditCheck("Biometric Security", "Hardware-backed biometrics available and enrolled", AuditStatus.SECURE))
        } else if (bio == BiometricStatus.DEVICE_NOT_SECURED) {
            score -= 25
            checks.add(AuditCheck("Device Lock", "Device screen lock is not configured", AuditStatus.WARNING))
        } else {
            checks.add(AuditCheck("Biometric Security", "Biometrics not configured on this hardware", AuditStatus.WARNING))
        }

        val auditStatus = when {
            score >= 80 -> AuditStatus.SECURE
            score >= 50 -> AuditStatus.WARNING
            else -> AuditStatus.CRITICAL
        }

        return FullSecurityAuditReport(score.coerceAtLeast(0), auditStatus, checks)
    }
}
