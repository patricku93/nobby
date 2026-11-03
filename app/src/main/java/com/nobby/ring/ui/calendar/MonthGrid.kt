package com.nobby.ring.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.ui.components.formatDate
import kotlinx.datetime.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val DAYS_PER_WEEK = 7

data class BandEdges(val start: Boolean, val end: Boolean)

/** Rounded band ends where the phase changes, or where the week row or the month starts or ends. */
fun bandEdges(cells: List<DayInfo?>, index: Int): BandEdges {
    val day = cells.getOrNull(index) ?: return BandEdges(start = false, end = false)
    val col = index % DAYS_PER_WEEK
    val prev = if (col > 0) cells.getOrNull(index - 1) else null
    val next = if (col < DAYS_PER_WEEK - 1) cells.getOrNull(index + 1) else null
    return BandEdges(start = prev?.phase != day.phase, end = next?.phase != day.phase)
}

@Composable
fun DayPhase.label(): String = when (this) {
    DayPhase.WEAR -> stringResource(R.string.phase_wear)
    DayPhase.PAUSE -> stringResource(R.string.phase_pause)
    DayPhase.NONE -> ""
}

/** Monday-first month grid with continuous highlighter bands per phase (bundle components/calendar.html). */
@Composable
fun MonthGrid(days: List<DayInfo>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val leading = days.firstOrNull()?.date?.dayOfWeek?.let { (it.ordinal - DayOfWeek.MONDAY.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK } ?: 0
    val cells: List<DayInfo?> = List(leading) { null } + days
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEachIndexed { w, week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until DAYS_PER_WEEK) {
                    val index = w * DAYS_PER_WEEK + i
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(52.dp)) {
                        if (day != null) DayCell(day, day.date == selected, bandEdges(cells, index)) { onSelect(day.date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: DayInfo, selected: Boolean, edges: BandEdges, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val band = when (day.phase) {
        DayPhase.WEAR -> c.primaryContainer
        DayPhase.PAUSE -> c.secondaryContainer
        DayPhase.NONE -> Color.Transparent
    }
    val description = if (day.phase == DayPhase.NONE) formatDate(day.date)
    else stringResource(R.string.calendar_cell_a11y, formatDate(day.date), day.phase.label())
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (day.phase == DayPhase.NONE) return@drawBehind
                val inset = 4.dp.toPx()
                val r = CornerRadius(14.dp.toPx())
                val zero = CornerRadius.Zero
                val shape = RoundRect(
                    left = if (edges.start) inset else 0f,
                    top = 7.dp.toPx(),
                    right = size.width - if (edges.end) inset else 0f,
                    bottom = size.height - 9.dp.toPx(),
                    topLeftCornerRadius = if (edges.start) r else zero,
                    topRightCornerRadius = if (edges.end) r else zero,
                    bottomRightCornerRadius = if (edges.end) r else zero,
                    bottomLeftCornerRadius = if (edges.start) r else zero,
                )
                drawPath(Path().apply { addRoundRect(shape) }, band)
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) c.inverseSurface else Color.Transparent)
                .then(if (day.isToday && !selected) Modifier.border(2.dp, c.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.event != null || day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> c.inverseOnSurface
                    day.phase == DayPhase.NONE -> c.outline
                    else -> c.onSurface
                },
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).height(7.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (day.event != null) Box(Modifier.size(7.dp).border(1.75.dp, c.onSurface, CircleShape))
            if (day.hasNote) Box(Modifier.size(6.dp).clip(CircleShape).background(c.tertiary))
        }
    }
}
