package com.example.security.settings

import android.content.Context
import android.content.SharedPreferences

object SecuritySettingsManager {
    private const val PREFS_NAME = "rr_security_settings"
    private var prefs: SharedPreferences? = null
    private var currentConfig = SecurityConfiguration()

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadConfiguration()
        }
    }

    fun getConfiguration(): SecurityConfiguration {
        return currentConfig
    }

    fun updateConfiguration(newConfig: SecurityConfiguration) {
        currentConfig = newConfig
        prefs?.edit()?.apply {
            putString("level", newConfig.level.name)
            putInt("maxConsecutiveFailures", newConfig.maxConsecutiveFailures)
            putLong("lockoutDurationSeconds", newConfig.lockoutDurationSeconds)
            putString("minAlertSeverity", newConfig.minAlertSeverity)
            putBoolean("requireBiometrics", newConfig.requireBiometricsForSensitiveOps)
            putBoolean("remoteLockdownEnabled", newConfig.remoteLockdownEnabled)
            apply()
        }
    }

    fun setSecurityLevel(level: SecurityLevel) {
        val updated = when (level) {
            SecurityLevel.STANDARD -> SecurityConfiguration(level, 5, 60, "HIGH", false, true)
            SecurityLevel.HIGH -> SecurityConfiguration(level, 3, 120, "MEDIUM", true, true)
            SecurityLevel.MAXIMUM -> SecurityConfiguration(level, 2, 300, "MEDIUM", true, true)
        }
        updateConfiguration(updated)
    }

    private fun loadConfiguration() {
        val p = prefs ?: return
        val levelName = p.getString("level", SecurityLevel.STANDARD.name) ?: SecurityLevel.STANDARD.name
        val level = try { SecurityLevel.valueOf(levelName) } catch (e: Exception) { SecurityLevel.STANDARD }
        val maxFails = p.getInt("maxConsecutiveFailures", 5)
        val lockout = p.getLong("lockoutDurationSeconds", 60)
        val minSev = p.getString("minAlertSeverity", "HIGH") ?: "HIGH"
        val biometrics = p.getBoolean("requireBiometrics", false)
        val lockdown = p.getBoolean("remoteLockdownEnabled", true)
        currentConfig = SecurityConfiguration(level, maxFails, lockout, minSev, biometrics, lockdown)
    }
}
