package com.nobby.ring.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.engine.SetupStatus
import com.nobby.ring.domain.model.OverdueConfig
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.components.ButtonTone
import com.nobby.ring.ui.components.NobbyButton
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.PermissionBanner
import com.nobby.ring.ui.components.PresetPicker
import com.nobby.ring.ui.components.SegmentedChoice
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.settings.MODE_ORDER
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class OnboardingActions(
    val onBack: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onFinish: () -> Unit = {},
    val onWear: (Int) -> Unit = {},
    val onPause: (Int) -> Unit = {},
    val onTime: (LocalTime) -> Unit = {},
    val onStatus: (SetupStatus) -> Unit = {},
    val onStatusDate: (LocalDate) -> Unit = {},
    val onStatusTime: (LocalTime) -> Unit = {},
    val onPalette: (Palette) -> Unit = {},
    val onMode: (ThemeMode) -> Unit = {},
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    // Preview the picked preset and mode live while onboarding; they are saved on finish.
    NobbyTheme(ThemeSpec(state.palette, state.mode)) {
        OnboardingContent(
            state,
            OnboardingActions(
                onBack = viewModel::back, onNext = viewModel::next, onFinish = viewModel::finish,
                onWear = viewModel::setWear, onPause = viewModel::setPause, onTime = viewModel::setTime,
                onStatus = viewModel::setStatus, onStatusDate = viewModel::setStatusDate, onStatusTime = viewModel::setStatusTime,
                onPalette = viewModel::setPalette, onMode = viewModel::setMode,
            ),
        )
    }
}

@Composable
fun OnboardingContent(state: OnboardingState, actions: OnboardingActions) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = NobbySpace.gutter, vertical = NobbySpace.l)) {
            StepBars(state.step)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = NobbySpace.xl, bottom = NobbySpace.l),
                verticalArrangement = Arrangement.spacedBy(NobbySpace.l),
            ) {
                StepIntro(state.step)
                when (state.step) {
                    0 -> StatusStep(state, actions)
                    1 -> RhythmStep(state, actions)
                    2 -> RemindersStep(state)
                    else -> LookStep(state, actions)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (state.step > 0) NobbyButton(actions.onBack, tone = ButtonTone.TEXT) { Text(stringResource(R.string.action_back)) } else Spacer(Modifier)
                if (state.step < OnboardingState.STEPS - 1) NobbyButton(actions.onNext, enabled = state.canNext) { Text(stringResource(R.string.action_next)) }
                else NobbyButton(actions.onFinish, enabled = !state.saving) { Text(stringResource(R.string.action_finish)) }
            }
        }
    }
}

@Composable
private fun StepBars(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(OnboardingState.STEPS) { i ->
            Box(
                Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

private data class Intro(val title: Int, val body: Int, val mood: MascotMood, val prop: MascotProp, val size: Dp)

private fun introFor(step: Int): Intro = when (step) {
    0 -> Intro(R.string.onb_status_title, R.string.onb_status_body, MascotMood.HAPPY, MascotProp.WAVE, 84.dp)
    1 -> Intro(R.string.onb_rhythm_title, R.string.onb_rhythm_body, MascotMood.NEUTRAL, MascotProp.CALENDAR, 80.dp)
    2 -> Intro(R.string.onb_reminders_title, R.string.onb_reminders_body, MascotMood.HAPPY, MascotProp.BELL, 80.dp)
    else -> Intro(R.string.onb_look_title, R.string.onb_look_body, MascotMood.HAPPY, MascotProp.PALETTE, 80.dp)
}

/** Eyebrow, title and body with this step's NoBby top-right, rotated 6° (the only mascot on the screen). */
@Composable
private fun StepIntro(step: Int) {
    val intro = introFor(step)
    val eyebrow = if (step == 0) stringResource(R.string.onb_step_first, step + 1, OnboardingState.STEPS)
    else stringResource(R.string.onb_step, step + 1, OnboardingState.STEPS)
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(end = 100.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(intro.title), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(intro.body),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Nobby(intro.mood, intro.size, Modifier.align(Alignment.TopEnd).offset(x = 8.dp).rotate(6f), prop = intro.prop)
    }
}

@Composable
private fun RhythmStep(state: OnboardingState, actions: OnboardingActions) {
    NobbyCard {
        NumberStepper(state.wearDays, actions.onWear, ScheduleConfig.MIN_WEAR..ScheduleConfig.MAX_WEAR, stringResource(R.string.onb_wear_days), hint = stringResource(R.string.onb_wear_hint))
        if (state.wearWarning) Text(stringResource(R.string.onb_safety_wear), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        NumberStepper(state.pauseDays, actions.onPause, ScheduleConfig.MIN_PAUSE..ScheduleConfig.MAX_PAUSE, stringResource(R.string.onb_pause_days), hint = stringResource(R.string.onb_pause_hint))
        if (state.pauseWarning) Text(stringResource(R.string.onb_safety_pause), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        TimePickerField(state.timeOfDay, actions.onTime, stringResource(R.string.onb_time_of_day))
    }
    InfoNote(stringResource(R.string.onb_safety_hint))
}

@Composable
private fun RemindersStep(state: OnboardingState) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Ask once when the step is entered (API 33+); the banner below stays for a second try.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    NobbyCard {
        ReminderRule.defaults(state.timeOfDay).forEach { rule ->
            val text = if (rule.offsetDays == 0) stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay!!))
            else plural(R.plurals.reminder_day_before, rule.offsetDays, rule.offsetDays, formatTime(rule.timeOfDay!!))
            Text(stringResource(R.string.onb_reminder_line, text, rule.importance.label()), Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
        }
        val every = OverdueConfig().repeatEveryHours
        Text(plural(R.plurals.onb_overdue_line, every, every), Modifier.padding(vertical = 6.dp), style = MaterialTheme.typography.bodyLarge)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionBanner(
            stringResource(R.string.onb_permission_body), Icons.Rounded.NotificationsActive, stringResource(R.string.action_allow),
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

@Composable
private fun LookStep(state: OnboardingState, actions: OnboardingActions) {
    PresetPicker(state.palette, actions.onPalette)
    Text(stringResource(R.string.appearance_mode), style = MaterialTheme.typography.titleMedium)
    SegmentedChoice(MODE_ORDER, state.mode, actions.onMode, label = { it.label() })
    InfoNote(stringResource(R.string.onb_calendar_hint))
}

/** Small butter hint (tertiaryContainer; dark: surfaceContainerHigh). */
@Composable
internal fun InfoNote(text: String) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NobbyRadius.md))
            .background(if (dark) c.surfaceContainerHigh else c.tertiaryContainer).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, Modifier.size(20.dp), tint = c.tertiary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (dark) c.onSurface else c.onTertiaryContainer)
    }
}
