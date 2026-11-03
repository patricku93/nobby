package com.nobby.ring.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.startScheduleDraft() }
    val d = draft ?: return

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_schedule)) },
            navigationIcon = { IconButton(onClick = { viewModel.discardDraft(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberStepper(d.wearDays, { v -> viewModel.updateDraft { it.copy(wearDays = v) } }, ScheduleConfig.MIN_WEAR..ScheduleConfig.MAX_WEAR, stringResource(R.string.onb_wear_days))
            if (d.wearWarning) Text(stringResource(R.string.onb_safety_wear), color = MaterialTheme.colorScheme.error)
            NumberStepper(d.pauseDays, { v -> viewModel.updateDraft { it.copy(pauseDays = v) } }, ScheduleConfig.MIN_PAUSE..ScheduleConfig.MAX_PAUSE, stringResource(R.string.onb_pause_days))
            if (d.pauseWarning) Text(stringResource(R.string.onb_safety_pause), color = MaterialTheme.colorScheme.error)
            TimePickerField(d.timeOfDay, { t -> viewModel.updateDraft { it.copy(timeOfDay = t, anchor = LocalDateTime(it.anchor.date, t)) } }, stringResource(R.string.onb_time_of_day))
            Text(stringResource(R.string.onb_anchor_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField(d.anchor.date, { date -> viewModel.updateDraft { it.copy(anchor = LocalDateTime(date, it.anchor.time)) } }, stringResource(R.string.onb_anchor_date))
                TimePickerField(d.anchor.time, { t -> viewModel.updateDraft { it.copy(anchor = LocalDateTime(it.anchor.date, t)) } }, stringResource(R.string.onb_anchor_time))
            }
            NumberStepper(d.horizonMonths, { v -> viewModel.updateDraft { it.copy(horizonMonths = v) } }, ScheduleConfig.MIN_HORIZON..ScheduleConfig.MAX_HORIZON, stringResource(R.string.schedule_horizon))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.schedule_preview_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.schedule_preview_body))
                    d.preview.forEach { e -> Text(stringResource(R.string.schedule_preview_row, e.kind.label(), formatDateTime(e.plannedAt))) }
                }
            }
            Button(onClick = { viewModel.applyDraft(); onBack() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.schedule_apply)) }
        }
    }
}
