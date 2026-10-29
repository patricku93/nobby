package com.nobby.ring.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_PER_WEEK = 7

/** Monday-first month grid. Plain Column/Row layout so it nests safely inside a vertically scrolling parent. */
@Composable
fun MonthGrid(days: List<DayInfo>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val leading = days.firstOrNull()?.date?.dayOfWeek?.let { (it.ordinal - DayOfWeek.MONDAY.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK } ?: 0
    val cells: List<DayInfo?> = List(leading) { null } + days
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f).padding(4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until DAYS_PER_WEEK) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f)) {
                        if (day != null) DayCell(day, day.date == selected) { onSelect(day.date) }
                        else Box(Modifier.aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: DayInfo, selected: Boolean, onClick: () -> Unit) {
    val ring = when (day.phase) {
        DayPhase.WEAR -> MaterialTheme.colorScheme.primary
        DayPhase.PAUSE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        DayPhase.NONE -> MaterialTheme.colorScheme.surface
    }
    val fill = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(3.dp).clip(CircleShape).background(fill)
            .border(if (day.phase == DayPhase.NONE) 0.dp else 3.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.date.dayOfMonth}", style = if (day.isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            if (day.hasNote || day.event != null) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (day.event != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary))
            }
        }
    }
}
