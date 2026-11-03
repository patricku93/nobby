package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/** The confirmed removal [from] re-keyed as [to] for the edited schedule (same planned and confirmed time). */
data class CarriedRemoval(val from: EventLog, val to: EventLog)

/**
 * Settings > Cycle & schedule > Apply regenerates every event id. A removal already confirmed for the current cycle
 * must stay confirmed, or the app shows the ring as in and its removal as overdue while the ring is out (I1).
 */
object ConfigChange {

    /**
     * Returns the current cycle's confirmed removal re-keyed to the edited schedule's current REMOVE, or null when there
     * is nothing to carry: no removal confirmed under [old]'s current REMOVE id, the id is unchanged, [new] has no
     * current REMOVE (no pause / skip pause / short horizon), or [new] describes a different ring.
     *
     * It is the same ring when the removal was confirmed after [new]'s start and the old and new current cycles
     * overlap. So a changed time of day, wear or pause length (start unchanged) or a corrected start date keep the
     * removal; a start date after the removal (ring back in) or a start moved to an earlier, disjoint cycle (D9) do not.
     */
    fun carriedRemoval(old: ScheduleConfig, new: ScheduleConfig, logs: List<EventLog>): CarriedRemoval? {
        val oldId = CycleCalculator.currentRemoveId(old)
        val log = logs.firstOrNull { it.kind == EventKind.REMOVE && it.eventId == oldId } ?: return null
        val newRemove = ScheduleEngine.generate(new).firstOrNull { it.cycleIndex == 0 && it.kind == EventKind.REMOVE } ?: return null
        if (newRemove.id == oldId) return null
        val sameRing = log.confirmedAt >= new.anchor && old.anchor < cycleEnd(new) && new.anchor < cycleEnd(old)
        return if (sameRing) CarriedRemoval(log, log.copy(eventId = newRemove.id)) else null
    }

    /** Planned start of cycle 1: the current cycle's INSERT time. */
    private fun cycleEnd(config: ScheduleConfig): LocalDateTime {
        val m = config.method
        return config.anchor.date.plus(m.wearDays + m.pauseDays, DateTimeUnit.DAY).atTime(m.timeOfDay)
    }
}
