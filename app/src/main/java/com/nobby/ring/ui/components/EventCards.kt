package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import kotlinx.datetime.LocalDateTime

/** Next-event card (bundle cards-banners "Next event" / "due today"). [actions] go in a row below. */
@Composable
fun NextEventCard(
    kind: EventKind,
    plannedAt: LocalDateTime,
    daysUntil: Int,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val due = daysUntil <= 0
    NobbyCard(modifier, tone = if (due) CardTone.TONAL else CardTone.PLAIN) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(
                if (due) Icons.Rounded.Alarm else Icons.Rounded.Event,
                if (kind == EventKind.INSERT) TileTone.PAUSE else TileTone.WEAR,
            )
            Column(Modifier.weight(1f)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (due) stringResource(R.string.home_due_today, kind.shortLabel(), formatTime(plannedAt.time))
                    else plural(R.plurals.home_next_in_days, daysUntil, formatDateTime(plannedAt), daysUntil),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (due) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actions != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** Overdue card (tomato container) with Done + Snooze and, beyond the safety threshold, the safety line. */
@Composable
fun OverdueCard(
    kind: EventKind,
    plannedAt: LocalDateTime,
    overdueText: String,
    safetyText: String?,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    NobbyCard(modifier, tone = CardTone.DANGER) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.Alarm, TileTone.ERROR)
            Column(Modifier.weight(1f)) {
                Text(kind.label(), style = MaterialTheme.typography.titleMedium)
                Text(overdueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            stringResource(R.string.home_overdue_body, formatDateTime(plannedAt)),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (safetyText != null) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(safetyText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NobbyButton(onDone, enabled = enabled, icon = Icons.Rounded.Check) { Text(stringResource(R.string.home_mark_done)) }
            NobbyButton(onSnooze, enabled = enabled, tone = ButtonTone.TEXT, icon = Icons.Rounded.Snooze, contentColor = LocalContentColor.current) {
                Text(stringResource(R.string.home_snooze))
            }
        }
    }
}
