package com.nobby.ring.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
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
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.ui.components.IconTile
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.TileTone
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatDuration
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class HistoryTiming { ON_TIME, LATE, EARLY }

/** Same 60-minute tolerance as the confirmation dialog. */
fun historyTiming(delay: Duration): HistoryTiming = when {
    delay.absoluteValue < ConfirmAction.CONFIRM_TOLERANCE_MINUTES.minutes -> HistoryTiming.ON_TIME
    delay.isPositive() -> HistoryTiming.LATE
    else -> HistoryTiming.EARLY
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    HistoryContent(entries)
}

@Composable
fun HistoryContent(entries: List<HistoryEntry>) {
    Scaffold(topBar = { NobbyTopBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        if (entries.isEmpty()) {
            EmptyHistory(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NobbySpace.gutter, vertical = NobbySpace.m),
                verticalArrangement = Arrangement.spacedBy(NobbySpace.s),
            ) { items(entries) { HistoryRow(it) } }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Nobby(MascotMood.NEUTRAL, 120.dp, prop = MascotProp.NOTE)
        Text(stringResource(R.string.history_empty_title), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.history_empty_body),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NobbyRadius.lg)
    val (icon, tone, title) = when (e.kind) {
        EventKind.REMOVE -> Triple(Icons.AutoMirrored.Rounded.Logout, TileTone.PAUSE, R.string.history_removed)
        EventKind.INSERT -> Triple(Icons.AutoMirrored.Rounded.Login, TileTone.WEAR, R.string.history_inserted)
        EventKind.SWAP -> Triple(Icons.Rounded.SwapHoriz, TileTone.WEAR, R.string.history_swapped)
    }
    val timing = historyTiming(e.delay)
    val timingText = when (timing) {
        HistoryTiming.ON_TIME -> stringResource(R.string.history_on_time)
        HistoryTiming.LATE -> stringResource(R.string.history_late, formatDuration(e.delay))
        HistoryTiming.EARLY -> stringResource(R.string.history_early, formatDuration(-e.delay))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (NobbyTheme.extra.dark) c.surfaceContainer else c.surfaceContainerLowest)
            .border(1.dp, c.outlineVariant, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tone, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.history_row_meta, formatDateTime(e.confirmedAt), formatTime(e.plannedAt.time)),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
        Text(
            timingText,
            style = MaterialTheme.typography.labelMedium,
            color = when (timing) {
                HistoryTiming.ON_TIME -> c.secondary
                HistoryTiming.LATE -> c.error
                HistoryTiming.EARLY -> c.onSurfaceVariant
            },
        )
    }
}
