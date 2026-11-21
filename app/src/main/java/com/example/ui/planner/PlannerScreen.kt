package com.example.ui.planner

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
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
import com.example.data.model.PlannerTaskEntity
import com.example.data.model.QuickNoteEntity
import com.example.timer.TimerManager
import com.example.timer.TimerService
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.PrimaryNeon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val todayString = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val tasks by db.plannerTaskDao().getTasksForDateFlow(todayString).collectAsState(initial = emptyList())
    val notes by db.quickNoteDao().getAllNotesFlow().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            listOf("Daily Plan", "Quick Notes", "Focus").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    if (tasks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No planner tasks for $todayString. Tap + to add.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(tasks, key = { it.id }) { task ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = task.isCompleted,
                                            onCheckedChange = { checked ->
                                                scope.launch {
                                                    db.plannerTaskDao().setTaskCompleted(task.id, checked)
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                task.taskText,
                                                fontWeight = FontWeight.Medium,
                                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                            Text(task.category, fontSize = 12.sp, color = AccentCyan)
                                        }
                                        IconButton(
                                            onClick = { scope.launch { db.plannerTaskDao().deleteTask(task) } },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = Color(0xFFEF4444))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { showTaskDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .testTag("add_planner_task_fab"),
                        containerColor = PrimaryNeon,
                        contentColor = Color.Black
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Planner Task")
                    }
                }

                1 -> {
                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No quick notes yet. Tap + to write one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(note.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            db.quickNoteDao().updateNote(note.copy(isPinned = !note.isPinned))
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.PushPin,
                                                        contentDescription = "Pin Note",
                                                        tint = if (note.isPinned) PrimaryNeon else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { scope.launch { db.quickNoteDao().deleteNote(note) } }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Note", tint = Color(0xFFEF4444))
                                                }
                                            }
                                        }
                                        if (note.content.isNotBlank()) {
                                            Text(note.content, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { showNoteDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                            .testTag("add_quick_note_fab"),
                        containerColor = AccentCyan,
                        contentColor = Color.Black
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Note")
                    }
                }

                2 -> {
                    val remaining by TimerManager.remainingSeconds.collectAsState()
                    val status by TimerManager.status.collectAsState()
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = "Focus", tint = PrimaryNeon, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Pomodoro Deep Focus", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            TimerManager.getFormattedRemaining(),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNeon
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    TimerManager.setDuration(0, 25, 0)
                                    val intent = Intent(context, TimerService::class.java).apply {
                                        action = TimerService.ACTION_START
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                            ) {
                                Text("Start 25m Focus", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            if (status.name == "RUNNING" || remaining > 0) {
                                OutlinedButton(onClick = {
                                    context.startService(
                                        Intent(context, TimerService::class.java).apply { action = TimerService.ACTION_RESET }
                                    )
                                }) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        var text by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Work") }
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = { Text("Add Daily Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Task") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            scope.launch {
                                db.plannerTaskDao().insertTask(
                                    PlannerTaskEntity(
                                        taskText = text.trim(),
                                        dateString = todayString,
                                        category = category.ifBlank { "General" }
                                    )
                                )
                            }
                            showTaskDialog = false
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showTaskDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNoteDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("New Quick Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Content") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            scope.launch {
                                db.quickNoteDao().insertNote(
                                    QuickNoteEntity(
                                        title = title.trim(),
                                        content = body.trim()
                                    )
                                )
                            }
                            showNoteDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") } }
        )
    }
}
