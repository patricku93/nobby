package com.nobby.ring.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

/**
 * Late (beyond the tolerance): the user chooses SHIFT or KEEP. Early: there is nothing to choose, the following dates are
 * always adjusted so the break stays within the limit, so a single Done button confirms with KEEP.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmDialog(
    pending: PendingConfirm,
    onTimeChange: (LocalDateTime) -> Unit,
    onPolicy: (ShiftPolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    val absDelay = abs(pending.delay.inWholeMinutes).minutes
    val early = pending.delay.isNegative()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (early) R.string.confirm_title_early else R.string.confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val planned = formatDateTime(pending.event.plannedAt)
                Text(if (early) stringResource(R.string.confirm_body_early, planned) else stringResource(R.string.confirm_body, planned, formatDuration(absDelay)))
                Text(stringResource(R.string.confirm_when))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(pending.confirmedAt.date, { onTimeChange(LocalDateTime(it, pending.confirmedAt.time)) }, stringResource(R.string.onb_anchor_date))
                    TimePickerField(pending.confirmedAt.time, { onTimeChange(LocalDateTime(pending.confirmedAt.date, it)) }, stringResource(R.string.onb_anchor_time))
                }
            }
        },
        confirmButton = {
            if (early) {
                Button(onClick = { onPolicy(ShiftPolicy.KEEP) }) { Text(stringResource(R.string.action_done)) }
            } else {
                Button(onClick = { onPolicy(ShiftPolicy.SHIFT) }) { Text(stringResource(R.string.confirm_shift)) }
            }
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!early) OutlinedButton(onClick = { onPolicy(ShiftPolicy.KEEP) }) { Text(stringResource(R.string.confirm_keep)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
