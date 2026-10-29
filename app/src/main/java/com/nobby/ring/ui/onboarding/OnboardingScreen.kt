package com.nobby.ring.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobby.ring.R
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.NumberStepper
import com.nobby.ring.ui.components.TimePickerField
import com.nobby.ring.ui.components.formatTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.theme.accent

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        LinearProgressIndicator(progress = { (state.step + 1f) / OnboardingState.STEPS }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        AnimatedContent(state.step, Modifier.weight(1f), label = "step") { step ->
            when (step) {
                0 -> WelcomeStep()
                1 -> ScheduleStep(state, viewModel)
                2 -> AnchorStep(state, viewModel)
                3 -> RemindersStep(state)
                else -> LookStep(state, viewModel)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.step > 0) TextButton(onClick = viewModel::back) { Text(stringResource(R.string.action_back)) } else Spacer(Modifier)
            if (state.step < OnboardingState.STEPS - 1) Button(onClick = viewModel::next) { Text(stringResource(R.string.action_next)) }
            else Button(onClick = viewModel::finish, enabled = !state.saving) { Text(stringResource(R.string.action_finish)) }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScheduleStep(state: OnboardingState, vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_schedule_title), style = MaterialTheme.typography.headlineMedium)
        NumberStepper(state.wearDays, vm::setWear, ScheduleConfig.MIN_WEAR..ScheduleConfig.MAX_WEAR, stringResource(R.string.onb_wear_days))
        if (state.wearWarning) Text(stringResource(R.string.onb_safety_wear), color = MaterialTheme.colorScheme.error)
        NumberStepper(state.pauseDays, vm::setPause, ScheduleConfig.MIN_PAUSE..ScheduleConfig.MAX_PAUSE, stringResource(R.string.onb_pause_days))
        if (state.pauseWarning) Text(stringResource(R.string.onb_safety_pause), color = MaterialTheme.colorScheme.error)
        TimePickerField(state.timeOfDay, vm::setTime, stringResource(R.string.onb_time_of_day))
    }
}

@Composable
private fun AnchorStep(state: OnboardingState, vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_anchor_title), style = MaterialTheme.typography.headlineMedium)
        DatePickerField(state.anchorDate, vm::setAnchorDate, stringResource(R.string.onb_anchor_date))
        TimePickerField(state.anchorTime, vm::setAnchorTime, stringResource(R.string.onb_anchor_time))
    }
}

@Composable
private fun RemindersStep(state: OnboardingState) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    // Ask once when the step is entered (API 33+); the button below stays for a second try.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_reminders_title), style = MaterialTheme.typography.headlineMedium)
        ReminderRule.defaults(state.timeOfDay).forEach { rule ->
            val text = if (rule.offsetDays == 0) stringResource(R.string.reminder_same_day, formatTime(rule.timeOfDay!!))
            else stringResource(R.string.reminder_day_before, rule.offsetDays, formatTime(rule.timeOfDay!!))
            Text("• $text (${rule.importance.label()})")
        }
        Text(stringResource(R.string.onb_reminders_body), style = MaterialTheme.typography.bodyMedium)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.onb_notifications_permission))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LookStep(state: OnboardingState, vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.onb_look_title), style = MaterialTheme.typography.headlineMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Palette.entries.forEach { p ->
                FilterChip(
                    selected = state.palette == p,
                    onClick = { vm.setPalette(p) },
                    label = { Text(p.label()) },
                    leadingIcon = { Box(Modifier.size(16.dp).clip(CircleShape).background(p.accent(false))) },
                )
            }
        }
        Text(stringResource(R.string.onb_calendar_connect) + " → " + stringResource(R.string.nav_settings), style = MaterialTheme.typography.bodyMedium)
    }
}
