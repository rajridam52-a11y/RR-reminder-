package com.example.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ClockScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Alarms", "Clock", "Timer", "Stopwatch", "World")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF00E676),
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF00E676)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, color = if (selectedTab == index) Color(0xFF00E676) else Color(0xFF94A3B8)) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> AlarmTab()
                1 -> Box(modifier = Modifier.fillMaxSize().padding(16.dp)) { DigitalClockWidget() }
                2 -> TimerTab()
                3 -> StopwatchTab()
                4 -> WorldClockTab()
            }
        }
    }
}
