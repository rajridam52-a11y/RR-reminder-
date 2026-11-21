package com.example.data.model

data class DashboardWidgetConfig(
    val id: String,
    val title: String,
    val isVisible: Boolean = true,
    val orderIndex: Int = 0
)

data class DashboardProfile(
    val name: String = "Default",
    val showNextAlarm: Boolean = true,
    val showTodayReminders: Boolean = true,
    val showWorldClockSummary: Boolean = true,
    val showWeatherBanner: Boolean = true,
    val showQuickNotes: Boolean = true,
    val showDailyPlanner: Boolean = true
)
