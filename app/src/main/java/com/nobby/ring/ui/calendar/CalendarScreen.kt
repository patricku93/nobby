package com.nobby.ring.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.NoteCard
import com.nobby.ring.ui.components.formatLongDate
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        NobbyTopBar(
            title = { Text(stringResource(R.string.nav_calendar)) },
            actions = { NobbyButton(viewModel::goToToday, tone = ButtonTone.TEXT) { Text(stringResource(R.string.calendar_today)) } },
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
            verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
        ) {
            MonthHeader(ui.year, ui.month, viewModel::prevMonth, viewModel::nextMonth)
            MonthGrid(ui.days, ui.selected, viewModel::select)
            Legend()
            SelectedDayCard(ui, viewModel::setNote)
        }
    }
}

@Composable
private fun MonthHeader(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.calendar_prev_month)) }
        Text(
            stringResource(R.string.calendar_month_title, Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()), year),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.calendar_next_month)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(stringResource(R.string.legend_wear)) { Box(Modifier.size(18.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.primaryContainer)) }
        LegendItem(stringResource(R.string.legend_pause)) { Box(Modifier.size(18.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(c.secondaryContainer)) }
        LegendItem(stringResource(R.string.legend_event)) { Box(Modifier.size(8.dp).border(1.75.dp, c.onSurface, CircleShape)) }
        LegendItem(stringResource(R.string.legend_note)) { Box(Modifier.size(7.dp).clip(CircleShape).background(c.tertiary)) }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Selected-day note card; a small flat NoBby keeps an empty day company (the only mascot on this screen). */
@Composable
private fun SelectedDayCard(ui: CalendarUiState, onNote: (String) -> Unit) {
    val day = ui.days.firstOrNull { it.date == ui.selected }
    NoteCard {
        Text(formatLongDate(ui.selected), style = MaterialTheme.typography.titleMedium)
        if (day != null && day.phase != DayPhase.NONE) {
            Text(
                stringResource(R.string.phase_day, day.phase.label(), day.dayInPhase, day.phaseLength),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.noteText.isBlank()) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Nobby(MascotMood.NEUTRAL, 40.dp, flat = true)
                Text(stringResource(R.string.calendar_note_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = ui.noteText,
            onValueChange = onNote,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text(stringResource(R.string.calendar_note_label)) },
            placeholder = { Text(stringResource(R.string.calendar_notes_hint)) },
            minLines = 3,
            shape = RoundedCornerShape(NobbyRadius.sm),
        )
    }
}
