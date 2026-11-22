package com.example.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingsRepository
import com.example.ui.clock.AlarmVisualTheme
import com.example.ui.clock.ClockAppearanceConfig
import com.example.ui.clock.ClockAppearanceStore
import com.example.ui.theme.RRReminderTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PREVIEW_MODE = "extra_preview_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val isPreview = intent.getBooleanExtra(EXTRA_PREVIEW_MODE, false)

        if (!isPreview) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                km?.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }

        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarm"
        val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 5)
        val isVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
        val soundTitle = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "Radar Beep"

        val settingsRepo = SettingsRepository(this)
        val themeMode = settingsRepo.getTheme()
        val appearanceConfig = ClockAppearanceStore.load(this)

        setContent {
            RRReminderTheme(themeMode = themeMode) {
                AlarmAlertScreen(
                    alarmLabel = label,
                    snoozeMinutes = snoozeMins,
                    appearance = appearanceConfig,
                    isPreview = isPreview,
                    onDismiss = {
                        if (!isPreview) {
                            val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
                                action = AlarmScheduler.ACTION_ALARM_DISMISS
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                            }
                            sendBroadcast(dismissIntent)
                        }
                        finish()
                    },
                    onSnooze = {
                        if (!isPreview) {
                            val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
                                action = AlarmScheduler.ACTION_ALARM_SNOOZE
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                                putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, isVibrate)
                                putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, soundTitle)
                            }
                            sendBroadcast(snoozeIntent)
                        }
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun AlarmAlertScreen(
    alarmLabel: String,
    snoozeMinutes: Int,
    appearance: ClockAppearanceConfig = ClockAppearanceConfig(),
    isPreview: Boolean = false,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    var currentTimeString by remember { mutableStateOf(getCurrentTimeString(appearance.use24Hour)) }
    var currentDateString by remember { mutableStateOf(getCurrentDateString()) }

    LaunchedEffect(appearance.use24Hour) {
        while (true) {
            currentTimeString = getCurrentTimeString(appearance.use24Hour)
            currentDateString = getCurrentDateString()
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val visualTheme: AlarmVisualTheme = appearance.alarmTheme
    val accent = appearance.accent
    val primaryTextColor = if (visualTheme.isAdaptiveMaterial) {
        MaterialTheme.colorScheme.onBackground
    } else {
        visualTheme.contentColor
    }
    val secondaryTextColor = if (visualTheme.isAdaptiveMaterial) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        visualTheme.secondaryContentColor
    }
    val bgBrush = if (visualTheme.isAdaptiveMaterial) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(visualTheme.startColor, visualTheme.endColor)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isPreview) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accent.primary.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = "STYLE PREVIEW • ${visualTheme.displayName.uppercase()}",
                                color = primaryTextColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = currentDateString,
                        color = secondaryTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentTimeString,
                        color = primaryTextColor,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = alarmLabel,
                        color = accent.primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .scale(pulseScale)
                        .background(accent.primary.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(102.dp)
                            .background(accent.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Ringing Alarm",
                            tint = accent.onAccent,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                if (isPreview) "Close Preview" else "Dismiss",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryTextColor)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Snooze, contentDescription = null, tint = primaryTextColor)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                "Snooze for $snoozeMinutes min",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getCurrentTimeString(use24Hour: Boolean): String {
    val pattern = if (use24Hour) "HH:mm:ss" else "hh:mm:ss a"
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date())
}

private fun getCurrentDateString(): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(Date())
}
