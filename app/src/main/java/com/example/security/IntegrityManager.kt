package com.example.security

import android.content.Context
import android.os.Build
import java.io.File

class IntegrityManager(private val context: Context) {

    fun performIntegrityAudit(): IntegrityReport {
        val checks = mutableListOf<IntegrityCheckResult>()

        val rootCheck = checkRootSignatures()
        checks.add(IntegrityCheckResult("Root Detection", !rootCheck, if (rootCheck) "Root binaries detected" else "No root binaries"))

        val debugCheck = android.os.Debug.isDebuggerConnected()
        checks.add(IntegrityCheckResult("Debugger Check", !debugCheck, if (debugCheck) "Debugger attached" else "No active debugger"))

        val testKeys = Build.TAGS != null && Build.TAGS.contains("test-keys")
        checks.add(IntegrityCheckResult("Build Tags", !testKeys, if (testKeys) "System contains test-keys" else "Release build tags"))

        val compromised = checks.any { !it.passed }
        return IntegrityReport(isCompromised = compromised, checks = checks)
    }

    private fun checkRootSignatures(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }
}
