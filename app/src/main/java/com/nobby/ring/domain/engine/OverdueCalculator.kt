package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.OverdueInfo
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object OverdueCalculator {

    fun nextPending(events: List<ScheduledEvent>, logs: List<EventLog>): ScheduledEvent? {
        val logged = logs.map { it.eventId }.toSet()
        return events.firstOrNull { it.id !in logged }
    }

    /**
     * Wear is measured from the planned cycle start; pause from the actual REMOVE confirmation when logged, else from the
     * planned remove time. A threshold is breached only beyond [ScheduleConfig.SAFE_MAX_WEAR] / [ScheduleConfig.SAFE_MAX_PAUSE]
     * days plus [ConfirmAction.CONFIRM_TOLERANCE_MINUTES]: a 60-minute grace matches the confirmation tolerance.
     */
    fun overdue(config: ScheduleConfig, events: List<ScheduledEvent>, logs: List<EventLog>, now: LocalDateTime): OverdueInfo? {
        val pending = nextPending(events, logs) ?: return null
        if (pending.plannedAt >= now) return null
        val delay = between(pending.plannedAt, now)
        val breached = when (pending.kind) {
            EventKind.REMOVE, EventKind.SWAP -> between(pending.cycleStart, now) > ScheduleConfig.SAFE_MAX_WEAR.days + grace
            EventKind.INSERT -> {
                val removedAt = logs.firstOrNull { it.kind == EventKind.REMOVE && it.plannedAt >= pending.cycleStart }?.confirmedAt
                    ?: config.removeTimeFor(pending.cycleStart)
                between(removedAt, now) > ScheduleConfig.SAFE_MAX_PAUSE.days + grace
            }
        }
        return OverdueInfo(pending, delay, breached)
    }

    private val grace: Duration = ConfirmAction.CONFIRM_TOLERANCE_MINUTES.minutes

    private fun between(from: LocalDateTime, to: LocalDateTime): Duration {
        val tz = TimeZone.UTC
        return to.toInstant(tz) - from.toInstant(tz)
    }

    private fun ScheduleConfig.removeTimeFor(cycleStart: LocalDateTime): LocalDateTime =
        cycleStart.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
}
