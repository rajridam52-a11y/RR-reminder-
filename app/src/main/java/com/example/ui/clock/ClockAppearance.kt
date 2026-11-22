package com.example.ui.clock

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ClockDesignStyle(
    val displayName: String,
    val subtitle: String
) {
    NEON_DIGITAL("Neon Digital", "Bold glowing readout"),
    CLASSIC_ANALOG("Classic Analog", "Dial face & sweeping hand"),
    RETRO_FLIP("Flip Clock", "Split-flap mechanical tiles"),
    ORBITAL_RING("Orbital Rings", "Concentric radial arcs"),
    MINIMAL_CLEAN("Minimal Studio", "Clean editorial typography"),
    DUAL_HORIZON("Horizon Split", "Stacked gradient progress")
}

enum class ClockAccentPreset(
    val displayName: String,
    val primary: Color,
    val secondary: Color,
    val onAccent: Color
) {
    EMERALD("Emerald", Color(0xFF00E676), Color(0xFF059669), Color.Black),
    CYAN("Electric Cyan", Color(0xFF00E5FF), Color(0xFF0284C7), Color.Black),
    COBALT("Cobalt", Color(0xFF3B82F6), Color(0xFF1D4ED8), Color.White),
    AMBER("Sunset Gold", Color(0xFFFFB300), Color(0xFFEA580C), Color.Black),
    CORAL("Rose Coral", Color(0xFFFB7185), Color(0xFFE11D48), Color.White),
    VIOLET("Royal Violet", Color(0xFFA78BFA), Color(0xFF7C3AED), Color.White)
}

enum class AlarmVisualTheme(
    val displayName: String,
    val subtitle: String,
    val startColor: Color,
    val endColor: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val isAdaptiveMaterial: Boolean = false
) {
    AURORA_GLOW(
        displayName = "Aurora Glow",
        subtitle = "Vibrant teal & indigo wave",
        startColor = Color(0xFF0F766E),
        endColor = Color(0xFF1E3A8A),
        contentColor = Color.White,
        secondaryContentColor = Color(0xFFCCFBF1)
    ),
    SUNRISE_WARM(
        displayName = "Morning Sunrise",
        subtitle = "Warm golden amber & coral",
        startColor = Color(0xFFFFEDD5),
        endColor = Color(0xFFFB923C),
        contentColor = Color(0xFF431407),
        secondaryContentColor = Color(0xFF7C2D12)
    ),
    DAYLIGHT_GLASS(
        displayName = "Daylight Sky",
        subtitle = "Bright airy sky frost",
        startColor = Color(0xFFF0F9FF),
        endColor = Color(0xFFBAE6FD),
        contentColor = Color(0xFF0F172A),
        secondaryContentColor = Color(0xFF1E40AF)
    ),
    CYBER_PULSE(
        displayName = "Ocean Pulse",
        subtitle = "Deep cobalt & cyan aura",
        startColor = Color(0xFF1E293B),
        endColor = Color(0xFF0C4A6E),
        contentColor = Color.White,
        secondaryContentColor = Color(0xFF7DD3FC)
    ),
    ADAPTIVE_CLEAN(
        displayName = "Clean Adaptive",
        subtitle = "Matches app Light/Dark theme",
        startColor = Color(0xFFE2E8F0),
        endColor = Color(0xFFCBD5E1),
        contentColor = Color(0xFF0F172A),
        secondaryContentColor = Color(0xFF334155),
        isAdaptiveMaterial = true
    )
}

enum class AlarmCardStyle(
    val displayName: String,
    val subtitle: String
) {
    MODERN_CARD("Modern Card", "Balanced elevated card"),
    GRADIENT_PILL("Soft Gradient", "Accent-tinted highlight"),
    MINIMAL_BORDER("Outlined Clean", "Crisp minimal border")
}

data class ClockAppearanceConfig(
    val clockStyle: ClockDesignStyle = ClockDesignStyle.NEON_DIGITAL,
    val accent: ClockAccentPreset = ClockAccentPreset.EMERALD,
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = true,
    val alarmTheme: AlarmVisualTheme = AlarmVisualTheme.AURORA_GLOW,
    val alarmCardStyle: AlarmCardStyle = AlarmCardStyle.MODERN_CARD
)

object ClockAppearanceStore {
    private val _state = MutableStateFlow<ClockAppearanceConfig?>(null)

    fun observe(context: Context): StateFlow<ClockAppearanceConfig?> {
        if (_state.value == null) {
            _state.value = load(context)
        }
        return _state
    }

    fun load(context: Context): ClockAppearanceConfig {
        val repo = SettingsRepository(context)
        return ClockAppearanceConfig(
            clockStyle = enumValueOrDefault(repo.getClockStyle(), ClockDesignStyle.NEON_DIGITAL),
            accent = enumValueOrDefault(repo.getClockAccent(), ClockAccentPreset.EMERALD),
            use24Hour = repo.isClock24Hour(),
            showSeconds = repo.isClockShowSeconds(),
            alarmTheme = enumValueOrDefault(repo.getAlarmTheme(), AlarmVisualTheme.AURORA_GLOW),
            alarmCardStyle = enumValueOrDefault(repo.getAlarmCardStyle(), AlarmCardStyle.MODERN_CARD)
        )
    }

    fun update(context: Context, transform: (ClockAppearanceConfig) -> ClockAppearanceConfig) {
        val next = transform(_state.value ?: load(context))
        val repo = SettingsRepository(context)
        repo.setClockStyle(next.clockStyle.name)
        repo.setClockAccent(next.accent.name)
        repo.setClock24Hour(next.use24Hour)
        repo.setClockShowSeconds(next.showSeconds)
        repo.setAlarmTheme(next.alarmTheme.name)
        repo.setAlarmCardStyle(next.alarmCardStyle.name)
        _state.value = next
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T {
        return try {
            enumValueOf<T>(raw)
        } catch (_: Exception) {
            fallback
        }
    }
}
