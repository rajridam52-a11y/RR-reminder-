package com.example.ui.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun ClockScreen() {
    val context = LocalContext.current
    val observedConfig by ClockAppearanceStore.observe(context).collectAsState()
    val config = observedConfig ?: ClockAppearanceStore.load(context)
    val accent = config.accent.primary

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Alarms", "Clock", "Timer", "Stopwatch", "World")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = accent,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = accent
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == index) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> AlarmTab()
                1 -> ClockCustomizerTab()
                2 -> TimerTab()
                3 -> StopwatchTab()
                4 -> WorldClockTab()
            }
        }
    }
}
