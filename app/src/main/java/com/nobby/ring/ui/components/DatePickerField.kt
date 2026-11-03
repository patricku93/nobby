package com.nobby.ring.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(date: LocalDate, onDateChange: (LocalDate) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, formatDate(date), Icons.Rounded.CalendarMonth, onClick = { open = true }, modifier = modifier)
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date) }
                    open = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}
