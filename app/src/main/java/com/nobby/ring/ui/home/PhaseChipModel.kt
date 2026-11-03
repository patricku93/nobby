package com.nobby.ring.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.components.plural
import com.nobby.ring.ui.ring.OverdueAmount

/** What the phase chip on Home says. While an event is overdue it shows the overdue amount instead of "day 9 of 7". */
sealed interface PhaseChipModel {
    val phase: PhaseKind

    data class InPhase(override val phase: PhaseKind, val day: Int, val length: Int) : PhaseChipModel
    data class OverDays(override val phase: PhaseKind, val days: Int) : PhaseChipModel
    data class OverHours(override val phase: PhaseKind, val hours: Int) : PhaseChipModel

    companion object {
        fun from(state: ScheduleState): PhaseChipModel {
            val p = state.position
            val amount = OverdueAmount.of(state) ?: return inPhase(p.phase, p.dayInPhase, p.phaseLengthDays)
            return overdue(p.phase, amount)
        }

        /** "Day X of N" with X never above N. */
        fun inPhase(phase: PhaseKind, day: Int, length: Int): InPhase = InPhase(phase, day.coerceAtMost(length.coerceAtLeast(1)), length)

        fun overdue(phase: PhaseKind, amount: OverdueAmount): PhaseChipModel = when (amount) {
            is OverdueAmount.Days -> OverDays(phase, amount.days)
            is OverdueAmount.Hours -> OverHours(phase, amount.hours)
        }
    }
}

@Composable
fun PhaseChipModel.text(): String = when (this) {
    is PhaseChipModel.InPhase -> stringResource(R.string.phase_day, phase.label(), day, length)
    is PhaseChipModel.OverDays -> plural(R.plurals.phase_over_days, days, phase.label(), days)
    is PhaseChipModel.OverHours -> plural(R.plurals.phase_over_hours, hours, phase.label(), hours)
}
