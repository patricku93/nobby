package com.nobby.ring.ui.settings
import com.nobby.ring.ui.components.nobbySegmentedColors
import com.nobby.ring.ui.components.NobbyButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.label
import kotlinx.datetime.LocalTime

@Composable
fun ReminderEditDialog(initial: ReminderRule, onSave: (ReminderRule) -> Unit, onDismiss: () -> Unit) {
    var rule by remember { mutableStateOf(initial) }
    var hoursMode by remember { mutableStateOf(initial.offsetHours != null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.reminder_add else R.string.reminder_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ReminderTarget.entries.forEachIndexed { i, t ->
                        SegmentedButton(selected = rule.target == t, onClick = { rule = rule.copy(target = t) }, shape = SegmentedButtonDefaults.itemShape(i, ReminderTarget.entries.size), colors = nobbySegmentedColors()) {
                            Text(stringResource(when (t) { ReminderTarget.ANY -> R.string.reminder_target_any; ReminderTarget.REMOVE -> R.string.reminder_target_remove; ReminderTarget.INSERT -> R.string.reminder_target_insert }))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !hoursMode, onClick = { hoursMode = false; rule = rule.copy(offsetHours = null, timeOfDay = rule.timeOfDay ?: LocalTime(20, 0)) }, label = { Text(stringResource(R.string.reminder_mode_days)) })
                    FilterChip(selected = hoursMode, onClick = { hoursMode = true; rule = rule.copy(offsetHours = rule.offsetHours ?: 2) }, label = { Text(stringResource(R.string.reminder_mode_hours)) })
                }
                if (hoursMode) {
                    NumberStepper(rule.offsetHours ?: 2, { rule = rule.copy(offsetHours = it) }, 1..72, stringResource(R.string.reminder_mode_hours))
                } else {
                    NumberStepper(rule.offsetDays, { rule = rule.copy(offsetDays = it) }, 0..14, stringResource(R.string.reminder_mode_days))
                    TimePickerField(rule.timeOfDay ?: LocalTime(20, 0), { rule = rule.copy(timeOfDay = it) }, stringResource(R.string.onb_time_of_day))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Importance.entries.forEach { imp ->
                        FilterChip(selected = rule.importance == imp, onClick = { rule = rule.copy(importance = imp) }, label = { Text(imp.label()) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reminder_enabled), Modifier.weight(1f))
                    Switch(checked = rule.enabled, onCheckedChange = { rule = rule.copy(enabled = it) })
                }
            }
        },
        confirmButton = { NobbyButton(onClick = { onSave(rule) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
