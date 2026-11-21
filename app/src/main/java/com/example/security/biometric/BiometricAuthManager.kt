package com.example.security.biometric

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build

class BiometricAuthManager(private val context: Context) {

    fun checkBiometricAvailability(): BiometricStatus {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManager = context.getSystemService(BiometricManager::class.java)
            if (biometricManager != null) {
                try {
                    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.BIOMETRIC_WEAK
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        biometricManager.canAuthenticate()
                    }
                    return when (result) {
                        BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.NO_HARDWARE
                        else -> if (keyguardManager?.isDeviceSecure == true) BiometricStatus.AVAILABLE else BiometricStatus.DEVICE_NOT_SECURED
                    }
                } catch (_: SecurityException) {
                    // Fall back safely to KeyguardManager if biometric service denies query
                } catch (_: Exception) {
                    // Fall back safely on OEM firmware quirks
                }
            }
        }
        return if (keyguardManager?.isKeyguardSecure == true || keyguardManager?.isDeviceSecure == true) {
            BiometricStatus.AVAILABLE
        } else {
            BiometricStatus.DEVICE_NOT_SECURED
        }
    }

    fun isDeviceSecured(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }
}
