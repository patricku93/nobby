package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

enum class ShiftPolicy { SHIFT, KEEP }

data class ConfirmResult(val config: ScheduleConfig, val logs: List<EventLog>)

object ConfirmAction {
    private val tz = TimeZone.UTC
    private const val MINUTES_PER_DAY = 1440L

    fun needsPolicyChoice(event: ScheduledEvent, confirmedAt: LocalDateTime): Boolean {
        val diff = confirmedAt.toInstant(tz) - event.plannedAt.toInstant(tz)
        return abs(diff.inWholeMinutes) >= 1.hours.inWholeMinutes
    }

    fun confirm(
        config: ScheduleConfig,
        event: ScheduledEvent,
        logs: List<EventLog>,
        confirmedAt: LocalDateTime,
        policy: ShiftPolicy,
    ): ConfirmResult = if (event.startsCycle) {
        confirmCycleStart(config, event, logs, confirmedAt, policy)
    } else {
        confirmRemove(config, event, logs, confirmedAt, policy)
    }

    /**
     * INSERT / SWAP: the new cycle starts at the confirmation. KEEP stays on the planned time unless the ring went in
     * early: an early insertion always re-anchors at the actual time so the wear phase is not under-counted.
     */
    private fun confirmCycleStart(
        config: ScheduleConfig,
        event: ScheduledEvent,
        logs: List<EventLog>,
        confirmedAt: LocalDateTime,
        policy: ShiftPolicy,
    ): ConfirmResult {
        val anchor = if (policy == ShiftPolicy.SHIFT || confirmedAt < event.plannedAt) confirmedAt else event.plannedAt
        return ConfirmResult(
            config.copy(anchor = anchor, skipPauseOnce = false),
            logs + EventLog(event.id, event.kind, event.plannedAt, confirmedAt),
        )
    }

    /**
     * REMOVE: moves the anchor by whole days (SHIFT: by the day difference; KEEP: not at all) but never so far that
     * the planned INSERT lands later than [confirmedAt] + pause, so the planned ring-free break never exceeds the
     * configured one (an early removal pulls the INSERT forward even with KEEP).
     */
    private fun confirmRemove(
        config: ScheduleConfig,
        event: ScheduledEvent,
        logs: List<EventLog>,
        confirmedAt: LocalDateTime,
        policy: ShiftPolicy,
    ): ConfirmResult {
        val eventLog = EventLog(event.id, event.kind, event.plannedAt, confirmedAt)
        val plannedInsert = ScheduleEngine.generate(config)
            .firstOrNull { it.kind == EventKind.INSERT && it.cycleIndex == 0 }
            ?.plannedAt
            ?: return confirmRemoveUnbounded(config, event, logs, confirmedAt, policy)
        val latestInsert = confirmedAt.date.plus(config.method.pauseDays, DateTimeUnit.DAY).atTime(confirmedAt.time)
        val maxShiftDays = Math.floorDiv(minutesBetween(plannedInsert, latestInsert), MINUTES_PER_DAY).toInt()
        val shiftDays = when (policy) {
            ShiftPolicy.SHIFT -> minOf(event.plannedAt.date.daysUntil(confirmedAt.date), maxShiftDays)
            ShiftPolicy.KEEP -> minOf(0, maxShiftDays)
        }
        if (shiftDays == 0) return ConfirmResult(config, logs + eventLog)
        val shifted = config.copy(anchor = config.anchor.date.plus(shiftDays, DateTimeUnit.DAY).atTime(config.anchor.time))
        return relogShiftedRemove(config, shifted, event, logs, confirmedAt)
    }

    /** Continuous use without a planned INSERT in the horizon: SHIFT moves the anchor by the exact delay. */
    private fun confirmRemoveUnbounded(
        config: ScheduleConfig,
        event: ScheduledEvent,
        logs: List<EventLog>,
        confirmedAt: LocalDateTime,
        policy: ShiftPolicy,
    ): ConfirmResult {
        if (policy == ShiftPolicy.KEEP) return ConfirmResult(config, logs + EventLog(event.id, event.kind, event.plannedAt, confirmedAt))
        val delay = confirmedAt.toInstant(tz) - event.plannedAt.toInstant(tz)
        val shifted = config.copy(anchor = (config.anchor.toInstant(tz) + delay).toLocalDateTime(tz))
        return relogShiftedRemove(config, shifted, event, logs, confirmedAt)
    }

    /**
     * The shifted schedule regenerates this REMOVE under a new id. The log is keyed by the shifted id so the
     * regenerated schedule sees the removal as confirmed, but it records the originally planned time so history
     * shows one removal with planned vs actual.
     */
    private fun relogShiftedRemove(
        config: ScheduleConfig,
        shifted: ScheduleConfig,
        event: ScheduledEvent,
        logs: List<EventLog>,
        confirmedAt: LocalDateTime,
    ): ConfirmResult {
        val shiftedRemove = ScheduleEngine.generate(shifted)
            .firstOrNull { it.kind == EventKind.REMOVE && it.cycleIndex == 0 }
            // Cannot happen for a valid cycle-0 REMOVE; degrade to KEEP behaviour.
            ?: return ConfirmResult(config, logs + EventLog(event.id, event.kind, event.plannedAt, confirmedAt))
        return ConfirmResult(shifted, logs + EventLog(shiftedRemove.id, EventKind.REMOVE, event.plannedAt, confirmedAt))
    }

    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime): Long =
        (to.toInstant(tz) - from.toInstant(tz)).inWholeMinutes
}
