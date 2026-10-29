package com.nobby.ring.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(onBack: () -> Unit, onCleanup: () -> Unit, viewModel: CalendarSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshPermission() }
    LaunchedEffect(Unit) { viewModel.refreshPermission() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_calendar)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (!ui.permissionGranted) {
                Text(stringResource(R.string.calendar_sync_permission))
                Button(onClick = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) {
                    Text(stringResource(R.string.onb_calendar_connect))
                }
                return@Column
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calendar_sync_enable), Modifier.weight(1f))
                Switch(checked = ui.enabled, onCheckedChange = { viewModel.setEnabled(it) })
            }
            if (ui.enabled) {
                Text(stringResource(R.string.calendar_sync_choose), style = MaterialTheme.typography.titleSmall)
                ui.calendars.forEach { cal ->
                    ListItem(
                        headlineContent = { Text(cal.name) },
                        supportingContent = { Text(cal.account) },
                        leadingContent = { RadioButton(selected = cal.id == ui.selectedId, onClick = { viewModel.select(cal.id) }) },
                    )
                }
                ui.lastSync?.let { Text(stringResource(R.string.calendar_sync_last, formatDateTime(it)), style = MaterialTheme.typography.bodySmall) }
                Row { OutlinedButton(onClick = { viewModel.syncNow() }) { Text(stringResource(R.string.calendar_sync_now)) } }
                OutlinedButton(onClick = onCleanup, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.calendar_cleanup)) }
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
