package com.example.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.RRReminderTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and show over lock screen
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

        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarm"
        val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 5)
        val isVibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
        val soundTitle = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "Radar Beep"

        setContent {
            RRReminderTheme {
                AlarmAlertScreen(
                    alarmLabel = label,
                    snoozeMinutes = snoozeMins,
                    onDismiss = {
                        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
                            action = AlarmScheduler.ACTION_ALARM_DISMISS
                            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                        }
                        sendBroadcast(dismissIntent)
                        finish()
                    },
                    onSnooze = {
                        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
                            action = AlarmScheduler.ACTION_ALARM_SNOOZE
                            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                            putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                            putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, isVibrate)
                            putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, soundTitle)
                        }
                        sendBroadcast(snoozeIntent)
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
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    var currentTimeString by remember { mutableStateOf(getCurrentTimeString()) }
    var currentDateString by remember { mutableStateOf(getCurrentDateString()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = getCurrentTimeString()
            currentDateString = getCurrentDateString()
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CobaltPrimary.copy(alpha = 0.35f),
                            Color(0xFF090D16)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentDateString,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentTimeString,
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = alarmLabel,
                        color = Color(0xFF38BDF8),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Pulsing Icon
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(pulseScale)
                        .background(CobaltPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(CobaltPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Ringing Alarm",
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                            Text("Dismiss", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Snooze, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Snooze for $snoozeMinutes min", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun getCurrentTimeString(): String {
    val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    return sdf.format(Date())
}

private fun getCurrentDateString(): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(Date())
}
