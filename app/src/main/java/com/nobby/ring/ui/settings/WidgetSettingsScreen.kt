package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import com.nobby.ring.domain.model.WidgetStyle
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.ring.RingModel
import kotlin.math.roundToInt

/** Below this opacity the settings explain that readability depends on the wallpaper. */
private const val READABLE_OPACITY = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val cfg by viewModel.widget.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val state by viewModel.schedule.collectAsStateWithLifecycle()
    // The slider moves locally and saves once on release, so dragging does not re-render the widget on every step.
    var dragOpacity by remember { mutableStateOf<Int?>(null) }
    val shown = cfg.copy(backgroundOpacity = dragOpacity ?: cfg.backgroundOpacity)
    Scaffold(topBar = {
        NobbyTopBar(
            title = { Text(stringResource(R.string.settings_widget)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.widget_preview), style = MaterialTheme.typography.titleMedium)
            WidgetPreview(shown, theme, state?.let(RingModel::from))
            // The slider sits right under the preview so both stay in view while dragging.
            OpacitySlider(
                value = shown.backgroundOpacity,
                onChange = { dragOpacity = it },
                onDone = {
                    dragOpacity?.let { viewModel.setWidget(cfg.copy(backgroundOpacity = it)) }
                    dragOpacity = null
                },
            )
            ModeChoice(cfg) { viewModel.setWidget(it) }
            if (cfg.mode == WidgetMode.SINGLE) StyleChoice(cfg) { viewModel.setWidget(it) }
            if (cfg.mode == WidgetMode.MULTI) RowChoice(cfg) { viewModel.setWidget(it) }
        }
    }
}

@Composable
private fun RadioLine(selected: Boolean, label: String, onClick: () -> Unit) {
    // The whole line is the touch target, not only the radio dot.
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
private fun ModeChoice(cfg: WidgetConfig, save: (WidgetConfig) -> Unit) {
    Text(stringResource(R.string.widget_mode), style = MaterialTheme.typography.titleMedium)
    WidgetMode.entries.forEach { m ->
        RadioLine(cfg.mode == m, stringResource(if (m == WidgetMode.SINGLE) R.string.widget_mode_single else R.string.widget_mode_multi)) {
            save(cfg.copy(mode = m))
        }
    }
}

@Composable
private fun StyleChoice(cfg: WidgetConfig, save: (WidgetConfig) -> Unit) {
    Text(stringResource(R.string.widget_style), style = MaterialTheme.typography.titleMedium)
    WidgetStyle.entries.forEach { s ->
        RadioLine(cfg.style == s, stringResource(if (s == WidgetStyle.RING) R.string.widget_style_ring else R.string.widget_style_classic)) {
            save(cfg.copy(style = s))
        }
    }
}

@Composable
private fun OpacitySlider(value: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.widget_background), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.widget_background_value, value), style = MaterialTheme.typography.titleMedium)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(WidgetConfig.snapOpacity(it.roundToInt())) },
        onValueChangeFinished = onDone,
        valueRange = 0f..WidgetConfig.MAX_OPACITY.toFloat(),
        // 0, 10, ..., 100: nine stops between the ends.
        steps = WidgetConfig.MAX_OPACITY / WidgetConfig.OPACITY_STEP - 1,
    )
    if (value < READABLE_OPACITY) {
        Text(stringResource(R.string.widget_background_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowChoice(cfg: WidgetConfig, save: (WidgetConfig) -> Unit) {
    Text(stringResource(R.string.widget_rows), style = MaterialTheme.typography.titleMedium)
    WidgetRow.entries.forEach { row ->
        val checked = row in cfg.rows
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                enabled = checked || cfg.rows.size < WidgetConfig.MAX_ROWS,
                onCheckedChange = { on -> save(cfg.copy(rows = if (on) cfg.rows + row else cfg.rows - row)) },
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
