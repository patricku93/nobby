package com.nobby.ring.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.BuildConfig
import com.nobby.ring.R
import com.nobby.ring.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            listOf(
                Triple(R.string.settings_schedule, Icons.Default.Schedule, Routes.SETTINGS_SCHEDULE),
                Triple(R.string.settings_reminders, Icons.Default.Notifications, Routes.SETTINGS_REMINDERS),
                Triple(R.string.settings_calendar, Icons.Default.CalendarMonth, Routes.SETTINGS_CALENDAR),
                Triple(R.string.settings_widget, Icons.Default.Widgets, Routes.SETTINGS_WIDGET),
                Triple(R.string.settings_appearance, Icons.Default.Palette, Routes.SETTINGS_APPEARANCE),
            ).forEach { (label, icon, route) ->
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { Icon(icon, null) },
                    modifier = Modifier.clickable { onNavigate(route) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME)) },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
