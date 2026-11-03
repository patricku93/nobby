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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.BeadRing
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.ChipTone
import com.nobby.ring.ui.components.NextEventCard
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyTopBar
import com.nobby.ring.ui.components.OverdueCard
import com.nobby.ring.ui.components.PermissionBanner
import com.nobby.ring.ui.components.PhaseChip
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.formatLongDate
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.components.shortLabel
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.ring.RingLabel
import com.nobby.ring.ui.ring.RingModel
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyType
import kotlinx.datetime.LocalDateTime

/** Callbacks of the Home screen, so [HomeContent] stays stateless and testable. */
data class HomeActions(
    val onDone: () -> Unit = {},
    val onSnooze: () -> Unit = {},
    val onToggleSkipPause: () -> Unit = {},
    val onConfirmTime: (LocalDateTime) -> Unit = {},
    val onPolicy: (ShiftPolicy) -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onDismissCelebration: () -> Unit = {},
    val onPermissionResult: () -> Unit = {},
)

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val exactAlarmsAllowed by viewModel.exactAlarmsAllowed.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose {}
    }
    HomeContent(
        ui, exactAlarmsAllowed, notificationsEnabled,
        HomeActions(
            onDone = viewModel::onDoneTapped,
            onSnooze = viewModel::snooze,
            onToggleSkipPause = viewModel::toggleSkipPause,
            onConfirmTime = viewModel::onConfirmTime,
            onPolicy = viewModel::onPolicyChosen,
            onDismissConfirm = viewModel::dismissConfirm,
            onDismissCelebration = viewModel::dismissCelebration,
            onPermissionResult = viewModel::refreshPermissions,
        ),
    )
}

@Composable
fun HomeContent(ui: HomeUiState, exactAlarmsAllowed: Boolean, notificationsEnabled: Boolean, actions: HomeActions) {
    val state = ui.state ?: return
    ui.pendingConfirm?.let { ConfirmDialog(it, actions.onConfirmTime, actions.onPolicy, actions.onDismissConfirm) }
    val model = RingModel.from(state)
    val compact = !exactAlarmsAllowed || !notificationsEnabled
    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { HomeTopBar(state) }) { padding ->
            // Layout budget: the Done button stays above the fold on a Pixel 3a (393 x 807 dp) with both banners.
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
            ) {
                if (compact) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NobbySpace.s)) {
                        if (!notificationsEnabled) NotificationsBanner(actions.onPermissionResult)
                        if (!exactAlarmsAllowed) ExactAlarmBanner()
                    }
                }
                PhaseChip(PhaseChipModel.from(state).text(), chipTone(state))
                BeadRing(
                    model.tone, model.beads, model.done,
                    Modifier.fillMaxWidth(if (compact) 0.6f else 0.72f).widthIn(max = if (compact) 220.dp else 264.dp),
                    // One mascot per screen: the sticker steps aside while the Done sheet shows the yay mascot.
                    sticker = if (ui.celebration == null) { s -> Nobby(model.mood, s) } else null,
                ) { ringSize -> RingCenter(model, ringSize) }
                if (model.safety) SafetyNotice()
                NextSection(state, model, ui.busy, actions)
            }
        }
        ui.celebration?.let { DoneSheet(it, actions.onDismissCelebration) }
    }
}

@Composable
private fun HomeTopBar(state: ScheduleState) {
    NobbyTopBar(title = {
        Column {
            Text(stringResource(R.string.app_name))
            Text(
                formatLongDate(state.now.date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    })
}

private fun chipTone(state: ScheduleState): ChipTone = when {
    state.overdue != null -> ChipTone.OVERDUE
    state.position.phase == PhaseKind.PAUSE -> ChipTone.PAUSE
    else -> ChipTone.WEAR
}

/** Number + label (+ sub line / safety pill) inside the ring; the number keeps its size at font scale 1.3. */
@Composable
private fun RingCenter(model: RingModel, ringSize: Dp) {
    val c = MaterialTheme.colorScheme
    val k = ringSize / 264.dp
    val numberSp = with(LocalDensity.current) { (96.dp * k).toSp() }
    val wordSp = with(LocalDensity.current) { (45.dp * k).toSp() }
    val urgent = model.tone == RingTone.OVERDUE || model.tone == RingTone.SAFETY
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (model.label == RingLabel.TODAY) {
            Text(stringResource(R.string.ring_today), style = NobbyType.countdownWord.copy(fontSize = wordSp, lineHeight = wordSp), color = c.onSurface)
        } else {
            model.number?.let {
                Text("$it", style = NobbyType.countdown.copy(fontSize = numberSp, lineHeight = numberSp), color = if (urgent) c.error else c.onSurface, maxLines = 1)
            }
        }
        Text(model.labelText(), style = MaterialTheme.typography.titleMedium, color = c.onSurface, textAlign = TextAlign.Center)
        model.subText()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
        if (model.safety) {
            Row(
                Modifier.padding(top = 8.dp).clip(CircleShape).background(c.error).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, Modifier.size(14.dp), tint = c.onError)
                Text(stringResource(R.string.ring_check_protection), style = MaterialTheme.typography.labelMedium, color = c.onError, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RingModel.labelText(): String {
    val n = number ?: 0
    return when (label) {
        RingLabel.UNTIL_REMOVE -> plural(R.plurals.ring_until_remove, n)
        RingLabel.UNTIL_INSERT -> plural(R.plurals.ring_until_insert, n)
        RingLabel.UNTIL_SWAP -> plural(R.plurals.ring_until_swap, n)
        RingLabel.OVERDUE_DAYS -> plural(R.plurals.ring_overdue_days, n)
        RingLabel.OVERDUE_HOURS -> plural(R.plurals.ring_overdue_hours, n)
        RingLabel.TODAY -> event?.kind?.shortLabel() ?: ""
        RingLabel.NONE -> stringResource(R.string.widget_none)
    }
}

@Composable
private fun RingModel.subText(): String? {
    val e = event ?: return null
    return when (label) {
        RingLabel.TODAY -> stringResource(R.string.ring_at_time, formatTime(e.plannedAt.time))
        RingLabel.OVERDUE_DAYS, RingLabel.OVERDUE_HOURS -> if (safety) null else stringResource(R.string.ring_since, formatDateTime(e.plannedAt))
        RingLabel.NONE -> null
        else -> formatDateTime(e.plannedAt)
    }
}

@Composable
private fun NextSection(state: ScheduleState, model: RingModel, busy: Boolean, actions: HomeActions) {
    val overdue = state.overdue
    if (overdue != null) {
        val n = model.number ?: 0
        val overText = if (model.label == RingLabel.OVERDUE_DAYS) plural(R.plurals.card_overdue_days, n, n) else plural(R.plurals.card_overdue_hours, n, n)
        val safety = if (!overdue.safetyBreached) null
        else stringResource(if (overdue.event.kind == EventKind.INSERT) R.string.home_overdue_safety_pause else R.string.home_overdue_safety_wear)
        OverdueCard(overdue.event.kind, overdue.event.plannedAt, overText, safety, actions.onDone, actions.onSnooze, enabled = !busy)
        return
    }
    val next = state.nextEvent ?: return
    val days = state.daysUntilNext ?: return
    NextEventCard(next.kind, next.plannedAt, days, actions = {
        if (state.isDue) {
            NobbyButton(actions.onDone, enabled = !busy, icon = Icons.Rounded.Check) { Text(stringResource(R.string.home_mark_done)) }
        }
        NobbyButton(actions.onToggleSkipPause, enabled = !busy, tone = ButtonTone.TEXT) {
            Text(stringResource(if (state.config.skipPauseOnce) R.string.home_skip_pause_cancel else R.string.home_skip_pause))
        }
    })
}

/** One-line notice above the cards when a safety threshold is crossed, so it is visible without scrolling. */
@Composable
private fun SafetyNotice() {
    val color = MaterialTheme.colorScheme.error
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Warning, contentDescription = null, Modifier.size(18.dp), tint = color)
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
private fun ExactAlarmBanner() {
    val ctx = LocalContext.current
    PermissionBanner(stringResource(R.string.exact_alarm_warning), Icons.Rounded.AlarmOff, stringResource(R.string.action_allow), onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.startSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }
    })
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
    PermissionBanner(stringResource(R.string.notifications_disabled_warning), Icons.Rounded.NotificationsOff, stringResource(R.string.action_allow), onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ctx.startSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    })
}

private fun Context.startSettings(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { Log.e("NoBby", "Cannot open settings: ${intent.action}", it) }
}
