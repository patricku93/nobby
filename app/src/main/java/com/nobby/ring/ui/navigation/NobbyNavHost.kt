package com.nobby.ring.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nobby.ring.R

@Composable
fun NobbyNavHost(startDestination: String) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in Routes.topLevel

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                listOf(
                    Triple(Routes.HOME, R.string.nav_home, Icons.Default.Home),
                    Triple(Routes.CALENDAR, R.string.nav_calendar, Icons.Default.CalendarMonth),
                    Triple(Routes.HISTORY, R.string.nav_history, Icons.Default.History),
                    Triple(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
                ).forEach { (dest, label, icon) ->
                    NavigationBarItem(
                        selected = route == dest,
                        onClick = { navController.navigate(dest) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label), maxLines = 1, softWrap = false, fontSize = 11.sp) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination, Modifier.padding(padding)) {
            composable(Routes.ONBOARDING) {
                com.nobby.ring.ui.onboarding.OnboardingScreen(onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) { com.nobby.ring.ui.home.HomeScreen() }
            composable(Routes.CALENDAR) { com.nobby.ring.ui.calendar.CalendarScreen() }
            composable(Routes.HISTORY) { com.nobby.ring.ui.history.HistoryScreen() }
            composable(Routes.SETTINGS) { com.nobby.ring.ui.settings.SettingsScreen(onNavigate = { navController.navigate(it) }) }
            composable(Routes.SETTINGS_SCHEDULE) { com.nobby.ring.ui.settings.ScheduleSettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_REMINDERS) { com.nobby.ring.ui.settings.RemindersScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_APPEARANCE) { com.nobby.ring.ui.settings.AppearanceScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_WIDGET) { com.nobby.ring.ui.settings.WidgetSettingsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS_CALENDAR) {
                com.nobby.ring.ui.settings.CalendarSettingsScreen(onBack = { navController.popBackStack() }, onCleanup = { navController.navigate(Routes.SETTINGS_CALENDAR_CLEANUP) })
            }
            composable(Routes.SETTINGS_CALENDAR_CLEANUP) {
                com.nobby.ring.ui.settings.CalendarCleanupScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
