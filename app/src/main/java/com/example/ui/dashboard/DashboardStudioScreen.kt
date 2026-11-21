package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.home.DashboardProfileStore

@Composable
fun DashboardStudioScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var profile by remember { mutableStateOf(DashboardProfileStore.load(context)) }

    fun update(block: (com.example.data.model.DashboardProfile) -> com.example.data.model.DashboardProfile) {
        val next = block(profile)
        profile = next
        DashboardProfileStore.save(context, next)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Choose which live modules appear on your Home Dashboard.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioToggle("Next Alarm Card", profile.showNextAlarm) { v ->
                        update { it.copy(showNextAlarm = v) }
                    }
                    StudioToggle("Active Reminders Summary", profile.showTodayReminders) { v ->
                        update { it.copy(showTodayReminders = v) }
                    }
                    StudioToggle("Indian Weather Preview", profile.showWeatherBanner) { v ->
                        update { it.copy(showWeatherBanner = v) }
                    }
                    StudioToggle("World Clock Strip", profile.showWorldClockSummary) { v ->
                        update { it.copy(showWorldClockSummary = v) }
                    }
                    StudioToggle("Pinned Notes Preview", profile.showQuickNotes) { v ->
                        update { it.copy(showQuickNotes = v) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
