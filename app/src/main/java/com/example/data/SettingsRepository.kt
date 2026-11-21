package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("rr_reminder_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_THEME = "app_theme" // "system", "dark", "light"
        private const val KEY_LANGUAGE = "app_language" // "en", "hi"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_ASSISTANT_TTS = "assistant_tts_enabled"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_DEFAULT_SNOOZE = "default_snooze_minutes"
        private const val KEY_APP_LOCK_PIN = "app_lock_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection_enabled"
    }

    fun getTheme(): String = prefs.getString(KEY_THEME, "system") ?: "system"
    fun setTheme(theme: String) = prefs.edit().putString(KEY_THEME, theme).apply()

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    fun setLanguage(lang: String) = prefs.edit().putString(KEY_LANGUAGE, lang).apply()

    fun isSoundEnabled(): Boolean = prefs.getBoolean(KEY_SOUND_ENABLED, true)
    fun setSoundEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()

    fun isVibrationEnabled(): Boolean = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    fun setVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()

    fun isAssistantTtsEnabled(): Boolean = prefs.getBoolean(KEY_ASSISTANT_TTS, true)
    fun setAssistantTtsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ASSISTANT_TTS, enabled).apply()

    fun isWakeWordEnabled(): Boolean = prefs.getBoolean(KEY_WAKE_WORD, false)
    fun setWakeWordEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply()

    fun getDefaultSnooze(): Int = prefs.getInt(KEY_DEFAULT_SNOOZE, 5)
    fun setDefaultSnooze(minutes: Int) = prefs.edit().putInt(KEY_DEFAULT_SNOOZE, minutes.coerceIn(1, 60)).apply()

    fun getAppLockPinHash(): String? = prefs.getString(KEY_APP_LOCK_PIN, null)
    fun setAppLockPinHash(hash: String?) = prefs.edit().putString(KEY_APP_LOCK_PIN, hash).apply()

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()

    fun isScreenshotProtectionEnabled(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, false)
    fun setScreenshotProtectionEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
}
