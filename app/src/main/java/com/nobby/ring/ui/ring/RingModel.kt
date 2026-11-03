package com.nobby.ring.ui.ring

import com.nobby.ring.domain.engine.OverdueCalculator
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood

enum class RingLabel { UNTIL_REMOVE, UNTIL_INSERT, UNTIL_SWAP, TODAY, OVERDUE_DAYS, OVERDUE_HOURS, NONE }

/** What the Home ring (and the widget) shows for a schedule state. Pure, so it is unit-tested. */
data class RingModel(
    val tone: RingTone,
    val mood: MascotMood,
    val beads: Int,
    val done: Int,
    /** Days (or hours when overdue for less than a day); null when the word "Heute" is shown or nothing is pending. */
    val number: Int?,
    val label: RingLabel,
    val event: ScheduledEvent?,
    val safety: Boolean,
) {
    companion object {
        fun from(state: ScheduleState): RingModel {
            val p = state.position
            val beads = p.phaseLengthDays.coerceAtLeast(1)
            val doneInPhase = (p.dayInPhase - 1).coerceIn(0, beads)
            val pause = p.phase == PhaseKind.PAUSE
            val calm = if (pause) RingTone.PAUSE else RingTone.WEAR
            val calmMood = if (pause) MascotMood.SLEEPY else MascotMood.HAPPY
            val next = state.nextEvent
            val days = state.daysUntilNext
            val overdue = state.overdue
            // A new ring still ahead after a break that is already longer than 7 days ("pause already over").
            val waiting = OverdueCalculator.breakBreached(state.events, state.logs, state.now)
            return when {
                overdue != null -> {
                    val amount = OverdueAmount.of(overdue.delay)
                    val byDays = amount is OverdueAmount.Days
                    RingModel(
                        tone = if (overdue.safetyBreached) RingTone.SAFETY else RingTone.OVERDUE,
                        mood = if (overdue.safetyBreached) MascotMood.ALERT else MascotMood.WORRIED,
                        beads = beads,
                        done = beads,
                        number = when (amount) {
                            is OverdueAmount.Days -> amount.days
                            is OverdueAmount.Hours -> amount.hours
                        },
                        label = if (byDays) RingLabel.OVERDUE_DAYS else RingLabel.OVERDUE_HOURS,
                        event = overdue.event,
                        safety = overdue.safetyBreached,
                    )
                }
                next == null || days == null -> RingModel(calm, calmMood, beads, doneInPhase, null, RingLabel.NONE, null, waiting)
                days <= 0 -> RingModel(RingTone.DUE, MascotMood.NEUTRAL, beads, beads, null, RingLabel.TODAY, next, waiting)
                else -> RingModel(calm, calmMood, beads, doneInPhase, days, labelFor(next.kind), next, waiting)
            }
        }

        private fun labelFor(kind: EventKind) = when (kind) {
            EventKind.REMOVE -> RingLabel.UNTIL_REMOVE
            EventKind.INSERT -> RingLabel.UNTIL_INSERT
            EventKind.SWAP -> RingLabel.UNTIL_SWAP
        }
    }
}
