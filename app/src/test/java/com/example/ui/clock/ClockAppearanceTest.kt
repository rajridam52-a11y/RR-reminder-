package com.example.ui.clock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClockAppearanceTest {

    @Test
    fun clockAndAlarmCustomizationsPersistAcrossStoreAndRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        ClockAppearanceStore.update(context) {
            it.copy(
                clockStyle = ClockDesignStyle.CLASSIC_ANALOG,
                accent = ClockAccentPreset.AMBER,
                use24Hour = true,
                showSeconds = false,
                alarmTheme = AlarmVisualTheme.SUNRISE_WARM,
                alarmCardStyle = AlarmCardStyle.GRADIENT_PILL
            )
        }

        val reloaded = ClockAppearanceStore.load(context)
        assertThat(reloaded.clockStyle).isEqualTo(ClockDesignStyle.CLASSIC_ANALOG)
        assertThat(reloaded.accent).isEqualTo(ClockAccentPreset.AMBER)
        assertThat(reloaded.use24Hour).isTrue()
        assertThat(reloaded.showSeconds).isFalse()
        assertThat(reloaded.alarmTheme).isEqualTo(AlarmVisualTheme.SUNRISE_WARM)
        assertThat(reloaded.alarmCardStyle).isEqualTo(AlarmCardStyle.GRADIENT_PILL)

        val repo = SettingsRepository(context)
        assertThat(repo.getAlarmTheme()).isEqualTo("SUNRISE_WARM")
        assertThat(repo.getClockStyle()).isEqualTo("CLASSIC_ANALOG")
    }
}
