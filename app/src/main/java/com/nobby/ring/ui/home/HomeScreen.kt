package com.nobby.ring.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.EventCard
import com.nobby.ring.ui.components.RingProgress
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val state = ui.state ?: return

    ui.pendingConfirm?.let {
        ConfirmDialog(it, viewModel::onConfirmTime, viewModel::onPolicyChosen, viewModel::dismissConfirm)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_home)) },
                actions = {
                    TextButton(onClick = viewModel::toggleSkipPause) {
                        Text(stringResource(if (state.config.skipPauseOnce) R.string.home_skip_pause_cancel else R.string.home_skip_pause))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            PhaseChip(state)
            Countdown(state)
            NextCard(state, viewModel::onDoneTapped)
        }
    }
}

@Composable
private fun PhaseChip(state: ScheduleState) {
    val p = state.position
    AssistChip(onClick = {}, label = { Text(stringResource(R.string.phase_day, p.phase.label(), p.dayInPhase, p.phaseLengthDays)) })
}

@Composable
private fun Countdown(state: ScheduleState) {
    val days = state.daysUntilNext ?: 0
    val p = state.position
    val progress = (p.dayInPhase.toFloat() / p.phaseLengthDays.coerceAtLeast(1)).coerceIn(0f, 1f)
    val overdue = days < 0
    val label = when {
        overdue -> stringResource(R.string.home_overdue_days)
        days == 0 -> stringResource(R.string.home_today)
        days == 1 -> stringResource(R.string.home_day_remaining)
        else -> stringResource(R.string.home_days_remaining)
    }
    RingProgress(
        progress = progress,
        modifier = Modifier.size(280.dp),
        progressColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (days != 0) Text("${abs(days)}", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun NextCard(state: ScheduleState, onDone: () -> Unit) {
    val next = state.nextEvent ?: return
    val overdue = state.overdue
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (overdue != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.home_overdue_title, overdue.event.kind.label()), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_overdue_body, formatDateTime(overdue.event.plannedAt)))
                    if (overdue.safetyBreached) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.home_overdue_safety), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        EventCard(next.kind, next.plannedAt, overdue = overdue != null, onDone = onDone)
        Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_mark_done)) }
    }
}
