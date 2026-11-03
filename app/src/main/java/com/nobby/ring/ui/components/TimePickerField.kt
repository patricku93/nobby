package com.nobby.ring.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(time: LocalTime, onTimeChange: (LocalTime) -> Unit, label: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, formatTime(time), Icons.Rounded.Schedule, onClick = { open = true }, modifier = modifier)
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onTimeChange(LocalTime(state.hour, state.minute)); open = false }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}
