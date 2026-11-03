package com.nobby.ring.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural

/** What the phase chip on Home says. While an event is overdue it shows the overdue amount instead of "day 9 of 7". */
sealed interface PhaseChipModel {
    val phase: PhaseKind

    data class InPhase(override val phase: PhaseKind, val day: Int, val length: Int) : PhaseChipModel
    data class OverDays(override val phase: PhaseKind, val days: Int) : PhaseChipModel
    data class OverHours(override val phase: PhaseKind, val hours: Int) : PhaseChipModel

    companion object {
        fun from(state: ScheduleState): PhaseChipModel {
            val p = state.position
            val overdue = state.overdue
                ?: return InPhase(p.phase, p.dayInPhase.coerceAtMost(p.phaseLengthDays.coerceAtLeast(1)), p.phaseLengthDays)
            val days = -(state.daysUntilNext ?: 0)
            return if (days >= 1) OverDays(p.phase, days) else OverHours(p.phase, overdue.delay.inWholeHours.toInt().coerceAtLeast(1))
        }
    }
}

@Composable
fun PhaseChipModel.text(): String = when (this) {
    is PhaseChipModel.InPhase -> stringResource(R.string.phase_day, phase.label(), day, length)
    is PhaseChipModel.OverDays -> plural(R.plurals.phase_over_days, days, phase.label(), days)
    is PhaseChipModel.OverHours -> plural(R.plurals.phase_over_hours, hours, phase.label(), hours)
}
