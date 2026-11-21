package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Clock : Screen("clock")
    object Reminders : Screen("reminders")
    object Planner : Screen("planner")
    object Weather : Screen("weather")
    object Assistant : Screen("assistant")
    object Settings : Screen("settings")
    object Security : Screen("security")
    object DashboardStudio : Screen("dashboard_studio")
}
