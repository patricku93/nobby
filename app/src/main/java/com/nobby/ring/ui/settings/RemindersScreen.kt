package com.nobby.ring.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.plural
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val overdue by viewModel.overdue.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ReminderRule?>(null) }

    editing?.let { rule ->
        ReminderEditDialog(rule, onSave = { viewModel.saveRule(it); editing = null }, onDismiss = { editing = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_reminders)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ReminderRule(0, ReminderTarget.ANY, 1, LocalTime(20, 0), null, Importance.NORMAL, true) }) {
                Icon(Icons.Default.Add, stringResource(R.string.reminder_add))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(rules, key = { it.id }) { rule ->
                val meta = stringResource(
                    if (rule.enabled) R.string.reminder_row_meta else R.string.reminder_row_meta_off,
                    targetText(rule.target),
                    rule.importance.label(),
                )
                ListItem(
                    headlineContent = { Text(ruleText(rule)) },
                    supportingContent = { Text(meta) },
                    trailingContent = { IconButton(onClick = { viewModel.deleteRule(rule.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete)) } },
                    modifier = Modifier.clickable { editing = rule },
                )
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    NumberStepper(
                        overdue.repeatEveryHours,
                        { viewModel.setOverdue(overdue.copy(repeatEveryHours = it)) },
                        OverdueConfigStore.MIN_REPEAT_HOURS..OverdueConfigStore.MAX_REPEAT_HOURS,
                        stringResource(R.string.overdue_repeat),
                    )
                    NumberStepper(
                        overdue.snoozeHours,
                        { viewModel.setOverdue(overdue.copy(snoozeHours = it)) },
                        OverdueConfigStore.MIN_SNOOZE_HOURS..OverdueConfigStore.MAX_SNOOZE_HOURS,
                        stringResource(R.string.overdue_snooze),
                    )
                }
            }
        }
    }
}

@Composable
private fun ruleText(rule: ReminderRule): String = when {
    rule.offsetHours != null -> plural(R.plurals.reminder_hours_before, rule.offsetHours, rule.offsetHours)
    rule.offsetDays == 0 -> stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
    else -> plural(R.plurals.reminder_day_before, rule.offsetDays, rule.offsetDays, formatTime(rule.timeOfDay ?: LocalTime(20, 0)))
}

@Composable
private fun targetText(t: ReminderTarget): String = stringResource(
    when (t) { ReminderTarget.ANY -> R.string.reminder_target_any; ReminderTarget.REMOVE -> R.string.reminder_target_remove; ReminderTarget.INSERT -> R.string.reminder_target_insert },
)
