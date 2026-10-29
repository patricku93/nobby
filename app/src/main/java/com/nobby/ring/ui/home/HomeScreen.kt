package com.nobby.ring.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    val exactAlarmsAllowed by viewModel.exactAlarmsAllowed.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }
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
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!exactAlarmsAllowed) ExactAlarmBanner()
            PhaseChip(state)
            Countdown(state)
            NextCard(state, ui.busy, viewModel::onDoneTapped)
        }
    }
}

@Composable
private fun ExactAlarmBanner() {
    val ctx = LocalContext.current
    TextButton(onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) }
        }
    }) { Text(stringResource(R.string.exact_alarm_warning), color = MaterialTheme.colorScheme.error) }
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
private fun NextCard(state: ScheduleState, busy: Boolean, onDone: () -> Unit) {
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
        EventCard(next.kind, next.plannedAt, overdue = overdue != null, onDone = null)
        Button(onClick = onDone, Modifier.fillMaxWidth(), enabled = !busy) { Text(stringResource(R.string.home_mark_done)) }
    }
}
