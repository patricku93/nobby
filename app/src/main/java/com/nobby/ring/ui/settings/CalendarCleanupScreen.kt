package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.formatDateTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarCleanupScreen(onBack: () -> Unit, viewModel: CalendarSettingsViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.calendar_cleanup)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(ui.query, viewModel::setQuery, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.cleanup_query)) })
            Row(Modifier.padding(vertical = 8.dp)) {
                DatePickerField(ui.from, viewModel::setFrom, stringResource(R.string.cleanup_from), Modifier.weight(1f))
                DatePickerField(ui.to, viewModel::setTo, stringResource(R.string.cleanup_to), Modifier.weight(1f))
            }
            Button(onClick = { viewModel.search() }, enabled = ui.query.isNotBlank()) { Text(stringResource(R.string.cleanup_search)) }
            if (ui.results.isEmpty()) Text(stringResource(R.string.cleanup_none), Modifier.padding(top = 16.dp))
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f)) {
                items(ui.results, key = { it.id }) { r ->
                    ListItem(
                        headlineContent = { Text(r.title) },
                        supportingContent = { Text(formatDateTime(Instant.fromEpochMilliseconds(r.startMillis).toLocalDateTime(TimeZone.currentSystemDefault()))) },
                        leadingContent = { Checkbox(checked = r.id in ui.selectedResults, onCheckedChange = { viewModel.toggleSelection(r.id) }) },
                    )
                }
            }
            Button(onClick = { viewModel.deleteSelected() }, enabled = ui.selectedResults.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cleanup_delete_selected, ui.selectedResults.size))
            }
        }
    }
}
