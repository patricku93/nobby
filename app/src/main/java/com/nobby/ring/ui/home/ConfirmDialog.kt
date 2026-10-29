package com.nobby.ring.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatDuration
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

@Composable
fun ConfirmDialog(
    pending: PendingConfirm,
    onTimeChange: (LocalDateTime) -> Unit,
    onPolicy: (ShiftPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    val absDelay = abs(pending.delay.inWholeMinutes).minutes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.confirm_body, formatDateTime(pending.event.plannedAt), formatDuration(absDelay)))
                Text(stringResource(R.string.confirm_when))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(pending.confirmedAt.date, { onTimeChange(LocalDateTime(it, pending.confirmedAt.time)) }, stringResource(R.string.onb_anchor_date))
                    TimePickerField(pending.confirmedAt.time, { onTimeChange(LocalDateTime(pending.confirmedAt.date, it)) }, stringResource(R.string.onb_anchor_time))
                }
            }
        },
        confirmButton = { Button(onClick = { onPolicy(ShiftPolicy.SHIFT) }) { Text(stringResource(R.string.confirm_shift)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { onPolicy(ShiftPolicy.KEEP) }) { Text(stringResource(R.string.confirm_keep)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
