package com.nobby.ring.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.OverdueInfo
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.EventCard
import com.nobby.ring.ui.components.RingProgress
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.theme.NobbyType
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val exactAlarmsAllowed by viewModel.exactAlarmsAllowed.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
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
        // Layout budget: the Done button must stay above the fold on a Pixel 3a (393 x 807 dp) with both banners.
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!exactAlarmsAllowed || !notificationsEnabled) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!notificationsEnabled) NotificationsBanner(onResult = viewModel::refreshPermissions)
                    if (!exactAlarmsAllowed) ExactAlarmBanner()
                }
            }
            PhaseChip(state)
            Countdown(state, compact = !exactAlarmsAllowed && !notificationsEnabled)
            NextCard(state, ui.busy, viewModel::onDoneTapped)
        }
    }
}

@Composable
private fun PermissionBanner(text: String, icon: ImageVector, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onErrorContainer
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExactAlarmBanner() {
    val ctx = LocalContext.current
    PermissionBanner(stringResource(R.string.exact_alarm_warning), Icons.Default.AlarmOff) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.startSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
    }
}

/**
 * The first tap on API 33+ asks for POST_NOTIFICATIONS. After the system dialog has been answered (or below API 33,
 * or when notifications were switched off in settings) taps open the app's notification settings instead.
 */
@Composable
private fun NotificationsBanner(onResult: () -> Unit) {
    val ctx = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        onResult()
    }
    PermissionBanner(stringResource(R.string.notifications_disabled_warning), Icons.Default.NotificationsOff) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ctx.startSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }
}

private fun Context.startSettings(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { Log.e("NoBby", "Cannot open settings: ${intent.action}", it) }
}

/** A non-interactive 32 dp chip; an AssistChip would reserve a 48 dp touch target for a label nobody taps. */
@Composable
private fun PhaseChip(state: ScheduleState) {
    Text(
        PhaseChipModel.from(state).text(),
        Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

@Composable
private fun Countdown(state: ScheduleState, compact: Boolean) {
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
        modifier = Modifier.fillMaxWidth(if (compact) 0.56f else 0.72f).widthIn(max = 240.dp).aspectRatio(1f),
        progressColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (days != 0) Text("${abs(days)}", style = NobbyType.countdown, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Event card and Done button come first so the button keeps the same above-the-fold position when overdue. A safety
 * breach adds a one-line notice above them so it is visible without scrolling; the full [OverdueCard] stays below.
 */
@Composable
private fun NextCard(state: ScheduleState, busy: Boolean, onDone: () -> Unit) {
    val next = state.nextEvent ?: return
    val overdue = state.overdue
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (overdue?.safetyBreached == true) SafetyNotice()
        EventCard(next.kind, next.plannedAt, overdue = overdue != null, onDone = null)
        Button(onClick = onDone, Modifier.fillMaxWidth(), enabled = !busy && state.isDue) { Text(stringResource(R.string.home_mark_done)) }
        if (overdue != null) OverdueCard(overdue)
    }
}

@Composable
private fun SafetyNotice() {
    val color = MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Warning, contentDescription = null, Modifier.size(18.dp), tint = color)
        Text(
            stringResource(R.string.notif_overdue_safety_short),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverdueCard(overdue: OverdueInfo) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_overdue_title, overdue.event.kind.label()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_overdue_body, formatDateTime(overdue.event.plannedAt)))
            if (overdue.safetyBreached) {
                Spacer(Modifier.height(8.dp))
                val safety = if (overdue.event.kind == EventKind.INSERT) R.string.home_overdue_safety_pause else R.string.home_overdue_safety_wear
                Text(stringResource(safety), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
