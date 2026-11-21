package com.example.ui.clock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stopwatch.StopwatchManager
import com.example.stopwatch.StopwatchStatus

@Composable
fun StopwatchTab() {
    val elapsedMillis by StopwatchManager.elapsedMillis.collectAsState()
    val status by StopwatchManager.status.collectAsState()
    val laps by StopwatchManager.laps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = StopwatchManager.formatTime(elapsedMillis),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF00E5FF)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (status) {
                StopwatchStatus.IDLE -> {
                    Button(
                        onClick = { StopwatchManager.start() },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("start_stopwatch_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Start", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                StopwatchStatus.RUNNING -> {
                    Button(
                        onClick = { StopwatchManager.recordLap() },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("lap_stopwatch_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                    ) {
                        Text("Lap", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { StopwatchManager.pause() },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                    ) {
                        Text("Pause", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                StopwatchStatus.PAUSED -> {
                    Button(
                        onClick = { StopwatchManager.start() },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Resume", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { StopwatchManager.reset() },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Reset", color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(laps, key = { it.lapNumber }) { lap ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Lap ${lap.lapNumber}", color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                        Text(
                            text = "+${lap.formattedLapTime}  (${lap.formattedTotalTime})",
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
