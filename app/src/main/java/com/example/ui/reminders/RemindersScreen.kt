package com.example.ui.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.model.ReminderEntity
import com.example.reminder.ReminderScheduler
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryNeon
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var selectedFilter by remember { mutableStateOf("Active") }
    val allReminders by db.reminderDao().getAllRemindersFlow().collectAsState(initial = emptyList())

    val filtered = remember(allReminders, selectedFilter) {
        when (selectedFilter) {
            "Active" -> allReminders.filter { !it.isCompleted }
            "Completed" -> allReminders.filter { it.isCompleted }
            else -> allReminders
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Active", "Completed", "All").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "Empty reminders",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No $selectedFilter reminders", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tap + to schedule a reminder with exact alarm alerts.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { rem ->
                        ReminderCard(
                            reminder = rem,
                            onToggleComplete = { checked ->
                                scope.launch {
                                    db.reminderDao().setReminderCompleted(rem.id, checked)
                                    if (checked) {
                                        ReminderScheduler.cancelReminder(context, rem.id)
                                    } else {
                                        ReminderScheduler.scheduleReminder(context, rem.copy(isCompleted = false))
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    ReminderScheduler.cancelReminder(context, rem.id)
                                    db.reminderDao().deleteReminder(rem)
                                }
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_reminder_fab"),
            containerColor = PrimaryNeon,
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Reminder")
        }
    }

    if (showCreateDialog) {
        CreateReminderDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, timeMillis, priority, category ->
                showCreateDialog = false
                scope.launch {
                    val entity = ReminderEntity(
                        title = title,
                        description = desc,
                        timeMillis = timeMillis,
                        priority = priority,
                        category = category
                    )
                    val id = db.reminderDao().insertReminder(entity)
                    ReminderScheduler.scheduleReminder(context, entity.copy(id = id))
                }
            }
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (reminder.priority) {
        "Urgent" -> Color(0xFFEF4444)
        "High" -> Color(0xFFFFB300)
        "Low" -> Color(0xFF38BDF8)
        else -> PrimaryNeon
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = reminder.isCompleted,
                onCheckedChange = onToggleComplete
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = reminder.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (reminder.description.isNotBlank()) {
                    Text(
                        text = reminder.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(reminder.category, fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text(reminder.priority, fontSize = 11.sp, color = priorityColor) }
                    )
                }
                Text(
                    text = reminder.formattedDateTime,
                    fontSize = 12.sp,
                    color = if (reminder.isOverdue) Color(0xFFEF4444) else AccentCyan
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Reminder", tint = Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var category by remember { mutableStateOf("General") }
    val cal = remember {
        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
    }
    var selectedMillis by remember { mutableStateOf(cal.timeInMillis) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reminder_title_input")
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                c.set(y, m, d)
                                selectedMillis = c.timeInMillis
                            },
                            c.get(Calendar.YEAR),
                            c.get(Calendar.MONTH),
                            c.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text("Pick Date")
                    }
                    OutlinedButton(onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = selectedMillis }
                        TimePickerDialog(
                            context,
                            { _, h, min ->
                                c.set(Calendar.HOUR_OF_DAY, h)
                                c.set(Calendar.MINUTE, min)
                                c.set(Calendar.SECOND, 0)
                                selectedMillis = c.timeInMillis
                            },
                            c.get(Calendar.HOUR_OF_DAY),
                            c.get(Calendar.MINUTE),
                            false
                        ).show()
                    }) {
                        Text("Pick Time")
                    }
                }
                Text("Priority: $priority", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Low", "Medium", "High", "Urgent").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p, fontSize = 11.sp) }
                        )
                    }
                }
                Text("Category: $category", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("General", "Work", "Personal", "Health").forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), desc.trim(), selectedMillis, priority, category)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_reminder_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
