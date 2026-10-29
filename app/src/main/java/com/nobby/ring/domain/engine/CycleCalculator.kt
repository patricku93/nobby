package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.CyclePosition
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime

object CycleCalculator {

    fun positionAt(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): CyclePosition {
        val method = config.method
        val dayInCycle = (now.date.toEpochDays() - config.anchor.date.toEpochDays()) + 1
        val removeConfirmed = logs.any { it.kind == EventKind.REMOVE && it.plannedAt >= config.anchor }
        val ringIn = !removeConfirmed
        return if (ringIn) {
            CyclePosition(PhaseKind.WEAR, dayInCycle, dayInCycle, method.cycleLengthDays, method.wearDays, ringIn = true)
        } else {
            val dayInPause = dayInCycle - method.wearDays
            CyclePosition(PhaseKind.PAUSE, dayInCycle, dayInPause, method.cycleLengthDays, method.pauseDays, ringIn = false)
        }
    }
}
