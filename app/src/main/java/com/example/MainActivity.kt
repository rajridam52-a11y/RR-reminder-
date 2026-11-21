package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.SettingsRepository
import com.example.integration.protection.EmergencyLockdownManager
import com.example.integration.protection.LockdownState
import com.example.ui.assistant.AssistantScreen
import com.example.ui.clock.ClockScreen
import com.example.ui.dashboard.DashboardStudioScreen
import com.example.ui.home.HomeScreen
import com.example.ui.navigation.Screen
import com.example.ui.planner.PlannerScreen
import com.example.ui.reminders.RemindersScreen
import com.example.ui.security.AppLockOverlay
import com.example.ui.security.EmergencyLockdownOverlay
import com.example.ui.settings.SettingsScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.RRReminderTheme
import com.example.ui.weather.WeatherScreen

private data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val navigateTargetState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val settingsRepo = SettingsRepository(this)
        applyScreenshotFlag(settingsRepo.isScreenshotProtectionEnabled())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        navigateTargetState.value = resolveDeepLinkRoute(intent)

        setContent {
            var themeMode by remember { mutableStateOf(settingsRepo.getTheme()) }
            var isAppUnlocked by remember {
                mutableStateOf(settingsRepo.getAppLockPinHash().isNullOrBlank())
            }
            val lockdownState by EmergencyLockdownManager.lockdownState.collectAsStateWithLifecycle()
            val deepLinkRoute by navigateTargetState

            RRReminderTheme(themeMode = themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RRReminderAppShell(
                        deepLinkRoute = deepLinkRoute,
                        onDeepLinkConsumed = { navigateTargetState.value = null },
                        onSettingsChanged = {
                            themeMode = settingsRepo.getTheme()
                            applyScreenshotFlag(settingsRepo.isScreenshotProtectionEnabled())
                        }
                    )

                    if (!isAppUnlocked) {
                        AppLockOverlay(onUnlocked = { isAppUnlocked = true })
                    }

                    if (lockdownState == LockdownState.ACTIVE) {
                        EmergencyLockdownOverlay(onReleased = { isAppUnlocked = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigateTargetState.value = resolveDeepLinkRoute(intent)
    }

    private fun resolveDeepLinkRoute(intent: Intent?): String? {
        return when (intent?.getStringExtra("navigate_to")) {
            "reminders" -> Screen.Reminders.route
            "timer", "clock" -> Screen.Clock.route
            "settings" -> Screen.Settings.route
            "weather" -> Screen.Weather.route
            "assistant" -> Screen.Assistant.route
            "home" -> Screen.Home.route
            else -> null
        }
    }

    private fun applyScreenshotFlag(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
private fun RRReminderAppShell(
    deepLinkRoute: String?,
    onDeepLinkConsumed: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topDestinations = remember {
        listOf(
            NavDestination(Screen.Home.route, "Home", Icons.Default.Home),
            NavDestination(Screen.Clock.route, "Clock", Icons.Default.Alarm),
            NavDestination(Screen.Reminders.route, "Tasks", Icons.Default.NotificationsActive),
            NavDestination(Screen.Weather.route, "Weather", Icons.Default.Cloud),
            NavDestination(Screen.Assistant.route, "Voice", Icons.Default.Mic),
            NavDestination(Screen.Settings.route, "Settings", Icons.Default.Settings)
        )
    }

    fun navigateSingleTop(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute != null) {
            navController.navigate(deepLinkRoute) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    val showChrome = currentRoute in topDestinations.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showChrome && !useRail) {
                    NavigationBar {
                        topDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateSingleTop(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showChrome && useRail) {
                    NavigationRail {
                        topDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateSingleTop(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(
                            onNavigateToHome = {
                                val target = deepLinkRoute ?: Screen.Home.route
                                navController.navigate(target) {
                                    popUpTo(Screen.Splash.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(onNavigate = { route -> navigateSingleTop(route) })
                    }
                    composable(Screen.Clock.route) {
                        ClockScreen()
                    }
                    composable(Screen.Reminders.route) {
                        RemindersScreen()
                    }
                    composable(Screen.Planner.route) {
                        PlannerScreen()
                    }
                    composable(Screen.Weather.route) {
                        WeatherScreen()
                    }
                    composable(Screen.Assistant.route) {
                        AssistantScreen(onNavigate = { route -> navigateSingleTop(route) })
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigate = { route -> navController.navigate(route) },
                            onSettingsChanged = onSettingsChanged
                        )
                    }
                    composable(Screen.DashboardStudio.route) {
                        DashboardStudioScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
