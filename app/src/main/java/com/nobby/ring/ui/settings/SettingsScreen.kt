package com.nobby.ring.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.BuildConfig
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.CardTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.SettingsDivider
import com.nobby.ring.ui.components.SettingsGroup
import com.nobby.ring.ui.components.SettingsRow
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.navigation.Routes
import com.nobby.ring.ui.theme.NobbySpace

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val overdue by viewModel.overdue.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val widget by viewModel.widget.collectAsStateWithLifecycle()
    var showLicences by rememberSaveable { mutableStateOf(false) }
    if (showLicences) LicenceDialog(onDismiss = { showLicences = false })

    Scaffold(topBar = { NobbyTopBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
            verticalArrangement = Arrangement.spacedBy(NobbySpace.xl),
        ) {
            SettingsGroup(stringResource(R.string.settings_group_ring)) {
                val method = schedule?.config?.method
                SettingsRow(
                    Icons.Rounded.Schedule, stringResource(R.string.settings_schedule),
                    summary = method?.let { stringResource(R.string.settings_schedule_summary, it.wearDays, it.pauseDays, formatTime(it.timeOfDay)) },
                    onClick = { onNavigate(Routes.SETTINGS_SCHEDULE) },
                )
                SettingsDivider()
                val active = rules.count { it.enabled }
                SettingsRow(
                    Icons.Rounded.Notifications, stringResource(R.string.settings_reminders),
                    summary = plural(R.plurals.settings_reminders_summary, active, active),
                    onClick = { onNavigate(Routes.SETTINGS_REMINDERS) },
                )
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Alarm, stringResource(R.string.settings_overdue),
                    summary = stringResource(R.string.settings_overdue_summary, overdue.repeatEveryHours, overdue.snoozeHours),
                    onClick = { onNavigate(Routes.SETTINGS_REMINDERS) },
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_look)) {
                SettingsRow(
                    Icons.Rounded.Palette, stringResource(R.string.settings_appearance),
                    summary = stringResource(R.string.settings_appearance_summary, theme.palette.label(), theme.mode.label()),
                    onClick = { onNavigate(Routes.SETTINGS_APPEARANCE) },
                )
            }
            SettingsGroup(stringResource(R.string.settings_group_more)) {
                SettingsRow(Icons.Rounded.CalendarMonth, stringResource(R.string.settings_calendar), onClick = { onNavigate(Routes.SETTINGS_CALENDAR) })
                SettingsDivider()
                SettingsRow(
                    Icons.Rounded.Widgets, stringResource(R.string.settings_widget),
                    summary = stringResource(if (widget.mode == WidgetMode.SINGLE) R.string.widget_mode_single else R.string.widget_mode_multi),
                    onClick = { onNavigate(Routes.SETTINGS_WIDGET) },
                )
            }
            AboutCard(onLicences = { showLicences = true })
        }
    }
}

/** Settings > Über NoBby: the only mascot on this screen (happy + wave), safety line right below. */
@Composable
private fun AboutCard(onLicences: () -> Unit) {
    NobbyCard(tone = CardTone.TONAL) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Nobby(MascotMood.HAPPY, 96.dp, prop = MascotProp.WAVE)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.settings_about_body), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
        NobbyButton(onLicences, Modifier.padding(top = 4.dp), tone = ButtonTone.TEXT, contentColor = LocalContentColor.current) {
            Text(stringResource(R.string.settings_licenses))
        }
    }
}

/** The two OFL licence texts from res/raw, shown offline. */
@Composable
private fun LicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        listOf(R.raw.ofl_fredoka, R.raw.ofl_nunito_sans).joinToString("\n\n") { id ->
            runCatching { context.resources.openRawResource(id).bufferedReader().use { it.readText() } }
                .onFailure { Log.e("NoBby", "Cannot read licence text $id", it) }
                .getOrDefault("")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { NobbyButton(onDismiss, tone = ButtonTone.TEXT) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Text(
                text,
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}
