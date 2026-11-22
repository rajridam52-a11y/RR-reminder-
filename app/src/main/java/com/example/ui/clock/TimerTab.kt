package com.example.ui.clock

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timer.TimerManager
import com.example.timer.TimerService
import com.example.timer.TimerStatus

@Composable
fun TimerTab() {
    val context = LocalContext.current
    val observedConfig by ClockAppearanceStore.observe(context).collectAsState()
    val config = observedConfig ?: ClockAppearanceStore.load(context)
    val accent = config.accent.primary
    val remainingSeconds by TimerManager.remainingSeconds.collectAsState()
    val totalSeconds by TimerManager.totalSeconds.collectAsState()
    val timerStatus by TimerManager.status.collectAsState()

    var inputMinutes by remember { mutableStateOf("5") }

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val progress = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 1f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                color = if (timerStatus == TimerStatus.COMPLETED) Color(0xFFFFB300) else accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (timerStatus == TimerStatus.IDLE || timerStatus == TimerStatus.COMPLETED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 25).forEach { preset ->
                    FilterChip(
                        selected = inputMinutes == preset.toString(),
                        onClick = {
                            inputMinutes = preset.toString()
                            TimerManager.setDuration(0, preset, 0)
                        },
                        label = { Text("${preset}m") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = inputMinutes,
                onValueChange = { ch ->
                    val filtered = ch.filter { it.isDigit() }.take(3)
                    inputMinutes = filtered
                    val mins = filtered.toIntOrNull()
                    if (mins != null && mins > 0) {
                        TimerManager.setDuration(0, mins, 0)
                    }
                },
                label = { Text("Duration (minutes)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .width(220.dp)
                    .testTag("timer_input")
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (timerStatus) {
                TimerStatus.IDLE, TimerStatus.COMPLETED -> {
                    Button(
                        onClick = {
                            val mins = (inputMinutes.toIntOrNull() ?: 5).coerceIn(1, 300)
                            TimerManager.setDuration(0, mins, 0)
                            val intent = Intent(context, TimerService::class.java).apply {
                                action = TimerService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("start_timer_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Start", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                TimerStatus.RUNNING -> {
                    Button(
                        onClick = {
                            context.startService(
                                Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_PAUSE }
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                    ) {
                        Text("Pause", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            context.startService(
                                Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESET }
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Reset", color = Color.White)
                    }
                }
                TimerStatus.PAUSED -> {
                    Button(
                        onClick = {
                            context.startService(
                                Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESUME }
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            context.startService(
                                Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESET }
                            )
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Reset", color = Color.White)
                    }
                }
            }
        }
    }
}
