package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.CyclePosition
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/**
 * Derives where "today" falls in the current cycle.
 *
 * Precondition: [ScheduleConfig.anchor] is the start of the *current* cycle; the repository
 * re-anchors on every confirmed INSERT/SWAP.
 *
 * Contract:
 * - `dayInCycle` and `dayInPhase` are always >= 1. When `now` is before the anchor (e.g. a
 *   future start date entered in onboarding) both are coerced to 1.
 * - While an event is overdue, `dayInPhase` may exceed `phaseLengthDays` (e.g. day 24 of 21).
 *   This is intentional so the UI can show "overdue"; the UI clamps progress itself.
 * - Pause days are counted from the *planned* remove date, not the confirmation time
 *   (consistent with the planned INSERT date under the KEEP policy).
 */
object CycleCalculator {

    fun positionAt(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): CyclePosition {
        val method = config.method
        val dayInCycle = ((now.date.toEpochDays() - config.anchor.date.toEpochDays()) + 1).coerceAtLeast(1)
        // A late removal confirmed with a shift is logged under the regenerated REMOVE id but keeps its original
        // plannedAt, which lies before the new anchor when the removal was more than wearDays late (v1.1 fix 3).
        val currentRemoveId = eventId(
            EventKind.REMOVE,
            config.anchor.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay),
        )
        val removeConfirmed = logs.any { it.kind == EventKind.REMOVE && (it.plannedAt >= config.anchor || it.eventId == currentRemoveId) }
        val ringIn = !removeConfirmed
        return if (ringIn) {
            CyclePosition(PhaseKind.WEAR, dayInCycle, dayInCycle, method.cycleLengthDays, method.wearDays, ringIn = true)
        } else {
            val dayInPause = (dayInCycle - method.wearDays).coerceAtLeast(1)
            CyclePosition(PhaseKind.PAUSE, dayInCycle, dayInPause, method.cycleLengthDays, method.pauseDays, ringIn = false)
        }
    }
}
