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
        private const val KEY_CLOCK_STYLE = "clock_design_style"
        private const val KEY_CLOCK_ACCENT = "clock_accent_preset"
        private const val KEY_CLOCK_24H = "clock_use_24_hour"
        private const val KEY_CLOCK_SECONDS = "clock_show_seconds"
        private const val KEY_ALARM_THEME = "alarm_visual_theme"
        private const val KEY_ALARM_CARD_STYLE = "alarm_card_style"
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

    fun getClockStyle(): String = prefs.getString(KEY_CLOCK_STYLE, "NEON_DIGITAL") ?: "NEON_DIGITAL"
    fun setClockStyle(style: String) = prefs.edit().putString(KEY_CLOCK_STYLE, style).apply()

    fun getClockAccent(): String = prefs.getString(KEY_CLOCK_ACCENT, "EMERALD") ?: "EMERALD"
    fun setClockAccent(accent: String) = prefs.edit().putString(KEY_CLOCK_ACCENT, accent).apply()

    fun isClock24Hour(): Boolean = prefs.getBoolean(KEY_CLOCK_24H, false)
    fun setClock24Hour(use24Hour: Boolean) = prefs.edit().putBoolean(KEY_CLOCK_24H, use24Hour).apply()

    fun isClockShowSeconds(): Boolean = prefs.getBoolean(KEY_CLOCK_SECONDS, true)
    fun setClockShowSeconds(showSeconds: Boolean) = prefs.edit().putBoolean(KEY_CLOCK_SECONDS, showSeconds).apply()

    fun getAlarmTheme(): String = prefs.getString(KEY_ALARM_THEME, "AURORA_GLOW") ?: "AURORA_GLOW"
    fun setAlarmTheme(theme: String) = prefs.edit().putString(KEY_ALARM_THEME, theme).apply()

    fun getAlarmCardStyle(): String = prefs.getString(KEY_ALARM_CARD_STYLE, "MODERN_CARD") ?: "MODERN_CARD"
    fun setAlarmCardStyle(style: String) = prefs.edit().putString(KEY_ALARM_CARD_STYLE, style).apply()
}
