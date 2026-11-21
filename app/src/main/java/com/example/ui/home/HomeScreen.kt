package com.example.ui.home

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppDatabase
import com.example.data.model.DashboardProfile
import com.example.ui.clock.DigitalClockWidget
import com.example.ui.navigation.Screen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.PrimaryNeon
import com.example.weather.WeatherRepository
import com.example.weather.model.IndianLocations
import com.example.weather.model.UIWeather

object DashboardProfileStore {
    private const val PREFS = "rr_dashboard_profile"
    private val _profileFlow = kotlinx.coroutines.flow.MutableStateFlow<DashboardProfile?>(null)

    fun observe(context: Context): kotlinx.coroutines.flow.StateFlow<DashboardProfile?> {
        if (_profileFlow.value == null) {
            _profileFlow.value = load(context)
        }
        return _profileFlow
    }

    fun load(context: Context): DashboardProfile {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DashboardProfile(
            name = sp.getString("name", "Default") ?: "Default",
            showNextAlarm = sp.getBoolean("show_next_alarm", true),
            showTodayReminders = sp.getBoolean("show_today_reminders", true),
            showWorldClockSummary = sp.getBoolean("show_world_clock", true),
            showWeatherBanner = sp.getBoolean("show_weather", true),
            showQuickNotes = sp.getBoolean("show_quick_notes", true),
            showDailyPlanner = sp.getBoolean("show_daily_planner", true)
        )
    }

    fun save(context: Context, profile: DashboardProfile) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("name", profile.name)
            .putBoolean("show_next_alarm", profile.showNextAlarm)
            .putBoolean("show_today_reminders", profile.showTodayReminders)
            .putBoolean("show_world_clock", profile.showWorldClockSummary)
            .putBoolean("show_weather", profile.showWeatherBanner)
            .putBoolean("show_quick_notes", profile.showQuickNotes)
            .putBoolean("show_daily_planner", profile.showDailyPlanner)
            .apply()
        _profileFlow.value = profile
    }
}

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val observedProfile by DashboardProfileStore.observe(context).collectAsState()
    val profile = observedProfile ?: remember { DashboardProfileStore.load(context) }

    val alarms by db.alarmDao().getAllAlarmsFlow().collectAsState(initial = emptyList())
    val activeReminders by db.reminderDao().getActiveRemindersFlow().collectAsState(initial = emptyList())
    val worldClocks by db.worldClockDao().getAllClocksFlow().collectAsState(initial = emptyList())
    val notes by db.quickNoteDao().getAllNotesFlow().collectAsState(initial = emptyList())

    var weather by remember { mutableStateOf<UIWeather?>(null) }
    LaunchedEffect(Unit) {
        val defaultCity = IndianLocations.citiesList.firstOrNull { it.name == "New Delhi" }
            ?: IndianLocations.citiesList.first()
        WeatherRepository().fetchWeather(defaultCity).onSuccess { weather = it }
    }

    val nextAlarm = alarms.firstOrNull { it.isEnabled }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.rr_dashboard_banner_1794083546526),
                        contentDescription = "RR Reminder Header Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x660B1120),
                                        Color(0xE60B1120)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "RR Reminder",
                            color = PrimaryNeon,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Smart Timekeeping & Reminders",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Created by Ridam Raj",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            DigitalClockWidget()
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    QuickActionTile(
                        title = "Planner",
                        icon = Icons.Default.EventNote,
                        accent = AccentCyan,
                        tag = "home_planner_tile",
                        onClick = { onNavigate(Screen.Planner.route) }
                    )
                }
                item {
                    QuickActionTile(
                        title = "Weather",
                        icon = Icons.Default.WbSunny,
                        accent = Color(0xFFFFB300),
                        tag = "home_weather_tile",
                        onClick = { onNavigate(Screen.Weather.route) }
                    )
                }
                item {
                    QuickActionTile(
                        title = "Assistant",
                        icon = Icons.Default.Mic,
                        accent = CobaltPrimary,
                        tag = "home_assistant_tile",
                        onClick = { onNavigate(Screen.Assistant.route) }
                    )
                }
                item {
                    QuickActionTile(
                        title = "Studio",
                        icon = Icons.Default.DashboardCustomize,
                        accent = Color(0xFFA78BFA),
                        tag = "home_studio_tile",
                        onClick = { onNavigate(Screen.DashboardStudio.route) }
                    )
                }
            }
        }

        if (profile.showNextAlarm) {
            item {
                DashboardSummaryCard(
                    title = "Next Scheduled Alarm",
                    icon = Icons.Default.Alarm,
                    accent = PrimaryNeon,
                    onClick = { onNavigate(Screen.Clock.route) }
                ) {
                    if (nextAlarm != null) {
                        Text(
                            text = nextAlarm.formattedTime,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${nextAlarm.label} • ${nextAlarm.repeatDaysSummary}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "No active alarms set. Tap to schedule one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (profile.showTodayReminders) {
            item {
                DashboardSummaryCard(
                    title = "Active Reminders (${activeReminders.size})",
                    icon = Icons.Default.NotificationsActive,
                    accent = AccentCyan,
                    onClick = { onNavigate(Screen.Reminders.route) }
                ) {
                    if (activeReminders.isEmpty()) {
                        Text(
                            "All caught up! Tap to create a reminder.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    } else {
                        activeReminders.take(3).forEach { rem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    rem.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    rem.formattedTime,
                                    color = AccentCyan,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (profile.showWeatherBanner) {
            item {
                DashboardSummaryCard(
                    title = "Indian Weather Live",
                    icon = Icons.Default.Cloud,
                    accent = Color(0xFFFFB300),
                    onClick = { onNavigate(Screen.Weather.route) }
                ) {
                    val w = weather
                    if (w != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${w.cityName}, ${w.stateName}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "${w.conditionDescription} • Humidity ${w.humidityDisplay}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                w.currentTemp,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    } else {
                        Text(
                            "Tap to inspect live weather across all 36 Indian States & UTs.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (profile.showWorldClockSummary && worldClocks.isNotEmpty()) {
            item {
                DashboardSummaryCard(
                    title = "World Clock",
                    icon = Icons.Default.Public,
                    accent = SkyAccentColor,
                    onClick = { onNavigate(Screen.Clock.route) }
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(worldClocks.take(4)) { wc ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(wc.cityName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(wc.formattedTime, color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        if (profile.showQuickNotes && notes.isNotEmpty()) {
            item {
                DashboardSummaryCard(
                    title = "Pinned Notes (${notes.size})",
                    icon = Icons.Default.StickyNote2,
                    accent = Color(0xFFA78BFA),
                    onClick = { onNavigate(Screen.Planner.route) }
                ) {
                    val top = notes.first()
                    Text(top.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    if (top.content.isNotBlank()) {
                        Text(
                            top.content.take(90),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

private val SkyAccentColor = Color(0xFF38BDF8)

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(104.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accent)
            }
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DashboardSummaryCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(20.dp))
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            content()
        }
    }
}
