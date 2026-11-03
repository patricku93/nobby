package com.nobby.ring.ui.settings
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyTopBar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(onBack: () -> Unit, onCleanup: () -> Unit, viewModel: CalendarSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshPermission() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermission() }

    Scaffold(topBar = {
        NobbyTopBar(
            title = { Text(stringResource(R.string.settings_calendar)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (!ui.permissionGranted) {
                Text(stringResource(R.string.calendar_sync_permission))
                NobbyButton(onClick = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) {
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
                if (ui.calendarsLoaded && ui.calendars.isEmpty()) Text(stringResource(R.string.calendar_sync_none), style = MaterialTheme.typography.bodySmall)
                if (ui.needsChoice && ui.calendars.isNotEmpty()) Text(stringResource(R.string.calendar_sync_pick_hint), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.selectableGroup()) {
                ui.calendars.forEach { cal ->
                    val selected = cal.id == ui.selectedId
                    ListItem(
                        headlineContent = { Text(cal.name) },
                        supportingContent = { Text(cal.account) },
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        // The colour comes from the calendar provider; an ARGB value without alpha is made opaque.
                        trailingContent = {
                            // Outline keeps white or very light calendars visible on the surface.
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal.color).copy(alpha = 1f)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                        },
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { viewModel.select(cal.id) }),
                    )
                }
                }
                ui.lastSync?.let { Text(stringResource(R.string.calendar_sync_last, formatDateTime(it)), style = MaterialTheme.typography.bodySmall) }
                Row { OutlinedButton(onClick = { viewModel.syncNow() }, enabled = !ui.needsChoice) { Text(stringResource(R.string.calendar_sync_now)) } }
                OutlinedButton(onClick = onCleanup, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.calendar_cleanup)) }
            }
            val problem = when {
                ui.selectedReadOnly -> stringResource(R.string.calendar_sync_read_only)
                ui.selectedMissing -> stringResource(R.string.calendar_sync_gone)
                else -> ui.error
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
