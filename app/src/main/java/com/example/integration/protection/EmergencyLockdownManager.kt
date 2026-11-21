package com.example.integration.protection

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.RRApplication
import com.example.security.crypto.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EmergencyLockdownManager {
    private const val TAG = "EmergencyLockdown"
    private const val PREFS = "rr_lockdown"
    private const val KEY_IS_LOCKED = "is_locked"
    private const val KEY_REASON = "reason"

    private var prefs: SharedPreferences? = null
    private val _lockdownState = MutableStateFlow(LockdownState.INACTIVE)
    val lockdownState: StateFlow<LockdownState> = _lockdownState.asStateFlow()
    private var lockdownReason: String = ""

    fun initialize(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = sp
        val active = sp.getBoolean(KEY_IS_LOCKED, false)
        lockdownReason = sp.getString(KEY_REASON, "") ?: ""
        _lockdownState.value = if (active) LockdownState.ACTIVE else LockdownState.INACTIVE
    }

    fun triggerLockdown(reason: String) {
        lockdownReason = reason
        _lockdownState.value = LockdownState.ACTIVE
        prefs?.edit()
            ?.putBoolean(KEY_IS_LOCKED, true)
            ?.putString(KEY_REASON, reason)
            ?.apply()
        Log.w(TAG, "EMERGENCY LOCKDOWN ACTIVATED: $reason")
    }

    fun releaseLockdown(pinOrKey: String, isRemoteAdminAuthorized: Boolean = false): Boolean {
        if (!isRemoteAdminAuthorized) {
            val storedPinHash = try {
                RRApplication.instance.settingsRepository.getAppLockPinHash()
            } catch (_: Exception) {
                null
            }
            if (!storedPinHash.isNullOrBlank()) {
                val verified = PasswordHasher.verifyPassword(pinOrKey, storedPinHash)
                if (!verified) {
                    Log.w(TAG, "Failed attempt to release emergency lockdown with invalid PIN")
                    return false
                }
            } else if (pinOrKey.isBlank()) {
                return false
            }
        }

        _lockdownState.value = LockdownState.INACTIVE
        lockdownReason = ""
        prefs?.edit()
            ?.putBoolean(KEY_IS_LOCKED, false)
            ?.putString(KEY_REASON, "")
            ?.apply()
        Log.i(TAG, "Emergency lockdown released")
        return true
    }

    fun isLockdownActive(): Boolean = _lockdownState.value == LockdownState.ACTIVE
    fun getLockdownReason(): String = lockdownReason
}
