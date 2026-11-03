package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

/**
 * The confirmed removal [from] re-keyed as [to] (same planned and confirmed time) for [config]: the edited schedule to
 * save, its anchor possibly moved by whole days so the planned break stays within the pause. [to] may keep [from]'s id.
 */
data class CarriedRemoval(val from: EventLog, val to: EventLog, val config: ScheduleConfig)

/**
 * Settings > Cycle & schedule > Apply regenerates every event id. A removal already confirmed for the current cycle
 * must stay confirmed, or the app shows the ring as in and its removal as overdue while the ring is out (I1).
 */
object ConfigChange {

    /**
     * Returns the current cycle's confirmed removal re-keyed to the edited schedule, or null when there is nothing to
     * carry: no removal confirmed under [old]'s current REMOVE id, the id is unchanged, [new] has no current REMOVE
     * (no pause / skip pause / short horizon), or [new] describes a different ring.
     *
     * It is the same ring when [new] starts on a day before the removal was confirmed and the old and new current
     * cycles overlap. So a changed time of day, wear or pause length (start unchanged) or a corrected start date keep
     * the removal; a start date on or after the removal's day (a new ring, I-B) or a start moved to an earlier,
     * disjoint cycle (D9) do not.
     *
     * Once the removal is logged, the edited schedule only decides the INSERT time, so it gets the same bound as a
     * confirmation with KEEP ([ConfirmAction.removeShiftDays]): the planned break never exceeds the configured pause
     * (I-A). [CarriedRemoval.config] is the schedule to save, possibly with its anchor moved by whole days; the
     * removal is keyed by that schedule's REMOVE id.
     */
    fun carriedRemoval(old: ScheduleConfig, new: ScheduleConfig, logs: List<EventLog>): CarriedRemoval? {
        val oldId = CycleCalculator.currentRemoveId(old)
        val log = logs.firstOrNull { it.kind == EventKind.REMOVE && it.eventId == oldId } ?: return null
        val newRemove = ScheduleEngine.generate(new).firstOrNull { it.cycleIndex == 0 && it.kind == EventKind.REMOVE } ?: return null
        if (newRemove.id == oldId) return null
        val sameRing = new.anchor.date < log.confirmedAt.date && old.anchor < cycleEnd(new) && new.anchor < cycleEnd(old)
        if (!sameRing) return null
        val shiftDays = ConfirmAction.removeShiftDays(new, newRemove, log.confirmedAt, ShiftPolicy.KEEP) ?: 0
        val config = if (shiftDays == 0) new else ConfirmAction.shiftAnchor(new, shiftDays)
        return CarriedRemoval(log, log.copy(eventId = CycleCalculator.currentRemoveId(config)), config)
    }

    /** The schedule Apply saves for [new]: what the Settings preview must show. */
    fun resolve(old: ScheduleConfig?, new: ScheduleConfig, logs: List<EventLog>): ScheduleConfig =
        old?.let { carriedRemoval(it, new, logs)?.config } ?: new

    /** Planned start of cycle 1: the current cycle's INSERT time. */
    private fun cycleEnd(config: ScheduleConfig): LocalDateTime {
        val m = config.method
        return config.anchor.date.plus(m.wearDays + m.pauseDays, DateTimeUnit.DAY).atTime(m.timeOfDay)
    }
}
