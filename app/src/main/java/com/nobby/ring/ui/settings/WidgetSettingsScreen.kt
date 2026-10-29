package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val cfg by viewModel.widget.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_widget)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.widget_mode), style = MaterialTheme.typography.titleMedium)
            WidgetMode.entries.forEach { m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = cfg.mode == m, onClick = { viewModel.setWidget(cfg.copy(mode = m)) })
                    Text(stringResource(if (m == WidgetMode.SINGLE) R.string.widget_mode_single else R.string.widget_mode_multi))
                }
            }
            if (cfg.mode == WidgetMode.MULTI) {
                Text(stringResource(R.string.widget_rows), style = MaterialTheme.typography.titleMedium)
                WidgetRow.entries.forEach { row ->
                    val checked = row in cfg.rows
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            enabled = checked || cfg.rows.size < WidgetConfig.MAX_ROWS,
                            onCheckedChange = { on -> viewModel.setWidget(cfg.copy(rows = if (on) cfg.rows + row else cfg.rows - row)) },
                        )
                        Text(stringResource(when (row) {
                            WidgetRow.NEXT_ANY -> R.string.widget_row_next_any
                            WidgetRow.NEXT_REMOVE -> R.string.widget_row_next_remove
                            WidgetRow.NEXT_INSERT -> R.string.widget_row_next_insert
                            WidgetRow.OVERDUE -> R.string.widget_row_overdue
                            WidgetRow.CYCLE_DAY -> R.string.widget_row_cycle_day
                        }))
                    }
                }
            }
        }
    }
}
