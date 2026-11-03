package com.nobby.ring.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                listOf(
                    NavItem(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
                    NavItem(Routes.CALENDAR, R.string.nav_calendar, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
                    NavItem(Routes.HISTORY, R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
                    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
                ).forEach { item ->
                    val selected = route == item.route
                    val c = MaterialTheme.colorScheme
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigate(item.route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.onPrimaryContainer,
                            selectedTextColor = c.onSurface,
                            indicatorColor = c.primaryContainer,
                            unselectedIconColor = c.onSurfaceVariant,
                            unselectedTextColor = c.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // consumeWindowInsets: the screens' own Scaffolds/TopAppBars must not add the status/navigation bar insets again.
        NavHost(navController, startDestination, Modifier.padding(padding).consumeWindowInsets(padding)) {
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

/** Filled icon when selected, outlined otherwise (bundle nav bar). */
private data class NavItem(val route: String, @StringRes val label: Int, val selectedIcon: ImageVector, val icon: ImageVector)
