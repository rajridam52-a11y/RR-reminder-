package com.example.ui.clock

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmScheduler
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.model.AlarmEntity
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AlarmTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val alarms by db.alarmDao().getAllAlarmsFlow().collectAsState(initial = emptyList())

    var pendingHour by remember { mutableStateOf<Int?>(null) }
    var pendingMinute by remember { mutableStateOf<Int?>(null) }
    var newLabel by remember { mutableStateOf("Wake Up") }
    var newVibrate by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (alarms.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "No alarms",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No alarms scheduled",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap + to add your first alarm with custom snooze and vibration.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onToggle = { isEnabled ->
                            scope.launch {
                                val updated = alarm.copy(isEnabled = isEnabled)
                                db.alarmDao().updateAlarm(updated)
                                if (isEnabled) {
                                    AlarmScheduler.scheduleAlarm(context, updated)
                                } else {
                                    AlarmScheduler.cancelAlarm(context, updated.id)
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                db.alarmDao().deleteAlarm(alarm)
                                AlarmScheduler.cancelAlarm(context, alarm.id)
                            }
                        }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val cal = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        pendingHour = hour
                        pendingMinute = minute
                        newLabel = "Alarm"
                        newVibrate = settingsRepo.isVibrationEnabled()
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_alarm_fab"),
            containerColor = Color(0xFF00E676),
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Alarm")
        }
    }

    if (pendingHour != null && pendingMinute != null) {
        AlertDialog(
            onDismissRequest = {
                pendingHour = null
                pendingMinute = null
            },
            title = { Text("Configure Alarm") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alarm_label_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibrate on ring")
                        Switch(checked = newVibrate, onCheckedChange = { newVibrate = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = pendingHour ?: 7
                        val m = pendingMinute ?: 0
                        pendingHour = null
                        pendingMinute = null
                        scope.launch {
                            val newAlarm = AlarmEntity(
                                hour = h,
                                minute = m,
                                isEnabled = true,
                                label = newLabel.ifBlank { "Alarm" },
                                isVibrate = newVibrate,
                                snoozeMinutes = settingsRepo.getDefaultSnooze()
                            )
                            val insertedId = db.alarmDao().insertAlarm(newAlarm)
                            AlarmScheduler.scheduleAlarm(context, newAlarm.copy(id = insertedId))
                        }
                    },
                    modifier = Modifier.testTag("save_alarm_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingHour = null
                    pendingMinute = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.formattedTime,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) Color.White else Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${alarm.label} • ${alarm.repeatDaysSummary}",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = Color(0xFFEF4444)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00E676),
                        checkedTrackColor = Color(0xFF00E676).copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}
