package com.nobby.ring.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.engine.SetupStatus
import com.nobby.ring.ui.components.DatePickerField
import com.nobby.ring.ui.components.NobbyCard
import com.nobby.ring.ui.components.TimePickerField

private val STATUS_ORDER = listOf(SetupStatus.RING_IN, SetupStatus.RING_OUT, SetupStatus.NOT_STARTED)

/** Step 1: where the user stands (radio list, whole row tappable), then when that happened or will happen. */
@Composable
internal fun StatusStep(state: OnboardingState, actions: OnboardingActions) {
    NobbyCard {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            STATUS_ORDER.forEach { s -> StatusOption(s, state.status == s) { actions.onStatus(s) } }
        }
    }
    val status = state.status ?: return
    if (state.pauseOver) InfoNote(stringResource(R.string.onb_status_pause_over))
    NobbyCard {
        Text(stringResource(questionFor(status)), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        DatePickerField(state.statusDate, actions.onStatusDate, stringResource(R.string.onb_anchor_date), Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        TimePickerField(state.statusTime, actions.onStatusTime, stringResource(R.string.onb_anchor_time), Modifier.fillMaxWidth())
        state.statusIssue?.let { issue ->
            Text(
                stringResource(if (issue == StatusIssue.IN_FUTURE) R.string.onb_status_in_future else R.string.onb_status_in_past),
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Pause already over and the new ring makes the break longer than 7 days: the same safety line as the overdue card.
        if (state.pauseSafety && state.statusIssue == null) {
            Text(
                stringResource(R.string.onb_status_pause_safety),
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusOption(status: SetupStatus, selected: Boolean, onClick: () -> Unit) {
    // The whole row is the touch target; the radio dot itself is not clickable separately.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("status_${status.name}")
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(labelFor(status)), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun labelFor(status: SetupStatus): Int = when (status) {
    SetupStatus.RING_IN -> R.string.onb_status_in
    SetupStatus.RING_OUT -> R.string.onb_status_out
    SetupStatus.NOT_STARTED -> R.string.onb_status_new
}

private fun questionFor(status: SetupStatus): Int = when (status) {
    SetupStatus.RING_IN -> R.string.onb_status_in_question
    SetupStatus.RING_OUT -> R.string.onb_status_out_question
    SetupStatus.NOT_STARTED -> R.string.onb_status_new_question
}
