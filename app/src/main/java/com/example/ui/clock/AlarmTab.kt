package com.example.ui.clock

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alarm.AlarmAlertActivity
import com.example.alarm.AlarmScheduler
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.data.model.AlarmEntity
import kotlinx.coroutines.launch
import java.util.Calendar

private val DAY_OPTIONS = listOf(
    2 to "M",
    4 to "T",
    8 to "W",
    16 to "T",
    32 to "F",
    64 to "S",
    128 to "S"
)

@Composable
fun AlarmTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val alarms by db.alarmDao().getAllAlarmsFlow().collectAsState(initial = emptyList())

    val observedConfig by ClockAppearanceStore.observe(context).collectAsState()
    val config = observedConfig ?: ClockAppearanceStore.load(context)

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showCustomizeDialog by remember { mutableStateOf(false) }

    fun launchPreview() {
        val intent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, "Morning Wake Up")
            putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, settingsRepo.getDefaultSnooze())
            putExtra(AlarmAlertActivity.EXTRA_PREVIEW_MODE, true)
        }
        context.startActivity(intent)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlarmStyleControlBar(
                config = config,
                onCustomizeClick = { showCustomizeDialog = true },
                onPreviewClick = { launchPreview() }
            )

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
                        tint = config.accent.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No alarms scheduled",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + to add an alarm or customize your ring screen design above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 92.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            config = config,
                            onEdit = {
                                editingAlarm = alarm
                                showEditorDialog = true
                            },
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
        }

        FloatingActionButton(
            onClick = {
                editingAlarm = null
                showEditorDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_alarm_fab"),
            containerColor = config.accent.primary,
            contentColor = config.accent.onAccent
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Alarm")
        }
    }

    if (showEditorDialog) {
        val initial = editingAlarm
        val now = Calendar.getInstance()
        AlarmEditorDialog(
            initialHour = initial?.hour ?: now.get(Calendar.HOUR_OF_DAY),
            initialMinute = initial?.minute ?: now.get(Calendar.MINUTE),
            initialLabel = initial?.label ?: "Wake Up",
            initialRepeatMask = initial?.repeatDaysMask ?: 0,
            initialVibrate = initial?.isVibrate ?: settingsRepo.isVibrationEnabled(),
            is24Hour = config.use24Hour,
            accent = config.accent,
            isEditing = initial != null,
            onDismiss = {
                showEditorDialog = false
                editingAlarm = null
            },
            onSave = { hour, minute, label, repeatMask, vibrate ->
                showEditorDialog = false
                val target = editingAlarm
                editingAlarm = null
                scope.launch {
                    if (target == null) {
                        val newAlarm = AlarmEntity(
                            hour = hour,
                            minute = minute,
                            isEnabled = true,
                            label = label.ifBlank { "Alarm" },
                            repeatDaysMask = repeatMask,
                            isVibrate = vibrate,
                            snoozeMinutes = settingsRepo.getDefaultSnooze()
                        )
                        val insertedId = db.alarmDao().insertAlarm(newAlarm)
                        AlarmScheduler.scheduleAlarm(context, newAlarm.copy(id = insertedId))
                    } else {
                        val updated = target.copy(
                            hour = hour,
                            minute = minute,
                            label = label.ifBlank { "Alarm" },
                            repeatDaysMask = repeatMask,
                            isVibrate = vibrate,
                            isEnabled = true
                        )
                        db.alarmDao().updateAlarm(updated)
                        AlarmScheduler.scheduleAlarm(context, updated)
                    }
                }
            }
        )
    }

    if (showCustomizeDialog) {
        AlarmDesignCustomizerDialog(
            config = config,
            onUpdate = { transform -> ClockAppearanceStore.update(context, transform) },
            onPreview = {
                showCustomizeDialog = false
                launchPreview()
            },
            onDismiss = { showCustomizeDialog = false }
        )
    }
}

@Composable
private fun AlarmStyleControlBar(
    config: ClockAppearanceConfig,
    onCustomizeClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    val theme = config.alarmTheme
    val bgBrush = if (theme.isAdaptiveMaterial) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surface
            )
        )
    } else {
        Brush.horizontalGradient(listOf(theme.startColor, theme.endColor))
    }
    val fg = if (theme.isAdaptiveMaterial) MaterialTheme.colorScheme.onSurface else theme.contentColor

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgBrush)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ring Style: ${theme.displayName}",
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${config.alarmCardStyle.displayName} • ${config.accent.displayName}",
                    color = fg.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(
                    onClick = onPreviewClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("alarm_preview_theme_btn")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview Alarm Screen")
                }
                Button(
                    onClick = onCustomizeClick,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("alarm_customize_btn"),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = config.accent.primary,
                        contentColor = config.accent.onAccent
                    )
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Style", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmEntity,
    config: ClockAppearanceConfig,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val accent = config.accent.primary
    val containerModifier = when (config.alarmCardStyle) {
        AlarmCardStyle.MODERN_CARD -> Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}")
        AlarmCardStyle.GRADIENT_PILL -> Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = if (alarm.isEnabled) {
                        listOf(accent.copy(alpha = 0.18f), config.accent.secondary.copy(alpha = 0.12f))
                    } else {
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
                    }
                )
            )
            .testTag("alarm_card_${alarm.id}")
        AlarmCardStyle.MINIMAL_BORDER -> Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}")
    }

    val cardColor = when (config.alarmCardStyle) {
        AlarmCardStyle.GRADIENT_PILL -> Color.Transparent
        else -> MaterialTheme.colorScheme.surface
    }
    val border = when (config.alarmCardStyle) {
        AlarmCardStyle.MINIMAL_BORDER -> BorderStroke(
            width = if (alarm.isEnabled) 1.5.dp else 1.dp,
            color = if (alarm.isEnabled) accent else MaterialTheme.colorScheme.outlineVariant
        )
        AlarmCardStyle.GRADIENT_PILL -> BorderStroke(
            width = 1.dp,
            color = if (alarm.isEnabled) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        )
        AlarmCardStyle.MODERN_CARD -> null
    }
    val shape = if (config.alarmCardStyle == AlarmCardStyle.GRADIENT_PILL) {
        RoundedCornerShape(22.dp)
    } else {
        RoundedCornerShape(18.dp)
    }

    Card(
        modifier = containerModifier.clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = border,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (config.alarmCardStyle == AlarmCardStyle.MODERN_CARD) 2.dp else 0.dp
        ),
        shape = shape
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
                    color = if (alarm.isEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${alarm.label} • ${alarm.repeatDaysSummary}",
                    fontSize = 13.sp,
                    color = if (alarm.isEnabled) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Alarm",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent,
                        checkedTrackColor = accent.copy(alpha = 0.35f)
                    )
                )
            }
        }
    }
}

@Composable
private fun AlarmEditorDialog(
    initialHour: Int,
    initialMinute: Int,
    initialLabel: String,
    initialRepeatMask: Int,
    initialVibrate: Boolean,
    is24Hour: Boolean,
    accent: ClockAccentPreset,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (hour: Int, minute: Int, label: String, repeatMask: Int, vibrate: Boolean) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )
    var useKeyboardInput by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(initialLabel) }
    var repeatMask by remember { mutableStateOf(initialRepeatMask) }
    var vibrate by remember { mutableStateOf(initialVibrate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isEditing) "Edit Alarm" else "New Alarm", fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { useKeyboardInput = !useKeyboardInput },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (useKeyboardInput) Icons.Default.Schedule else Icons.Default.Keyboard,
                        contentDescription = "Switch time input mode"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (useKeyboardInput) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Alarm Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_label_input")
                )

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Repeat Days", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DAY_OPTIONS.forEach { (bit, shortName) ->
                            val active = (repeatMask and bit) != 0
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (active) accent.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        repeatMask = if (active) (repeatMask and bit.inv()) else (repeatMask or bit)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) accent.onAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibrate on ring")
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(timePickerState.hour, timePickerState.minute, label, repeatMask, vibrate)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.primary,
                    contentColor = accent.onAccent
                ),
                modifier = Modifier.testTag("save_alarm_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AlarmDesignCustomizerDialog(
    config: ClockAppearanceConfig,
    onUpdate: ((ClockAppearanceConfig) -> ClockAppearanceConfig) -> Unit,
    onPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Alarm Design", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Ringing Screen Background", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(AlarmVisualTheme.entries) { theme ->
                        val selected = config.alarmTheme == theme
                        val previewBrush = if (theme.isAdaptiveMaterial) {
                            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
                        } else {
                            Brush.verticalGradient(listOf(theme.startColor, theme.endColor))
                        }
                        val fg = if (theme.isAdaptiveMaterial) MaterialTheme.colorScheme.onSurface else theme.contentColor

                        Box(
                            modifier = Modifier
                                .width(122.dp)
                                .height(86.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(previewBrush)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) config.accent.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onUpdate { it.copy(alarmTheme = theme) } }
                                .padding(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("07:00", fontWeight = FontWeight.Black, fontSize = 15.sp, color = fg)
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = config.accent.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(theme.displayName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = fg)
                            }
                        }
                    }
                }

                Text("Alarm List Card Style", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlarmCardStyle.entries.forEach { cardStyle ->
                        val selected = config.alarmCardStyle == cardStyle
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onUpdate { it.copy(alarmCardStyle = cardStyle) } }
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) config.accent.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            color = if (selected) config.accent.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(cardStyle.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(cardStyle.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (selected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = config.accent.primary)
                                }
                            }
                        }
                    }
                }

                Text("Accent Color", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ClockAccentPreset.entries.forEach { preset ->
                        val selected = config.accent == preset
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(preset.primary)
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { onUpdate { it.copy(accent = preset) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = preset.displayName, tint = preset.onAccent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPreview,
                colors = ButtonDefaults.buttonColors(
                    containerColor = config.accent.primary,
                    contentColor = config.accent.onAccent
                )
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Preview Ring", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
