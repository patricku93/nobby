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
     * Wear is measured from the planned cycle start; pause from the actual confirmation of this cycle's REMOVE (found by
     * id) when logged, else from the planned remove time. A threshold is breached only beyond [ScheduleConfig.SAFE_MAX_WEAR] / [ScheduleConfig.SAFE_MAX_PAUSE]
     * days plus [ConfirmAction.CONFIRM_TOLERANCE_MINUTES]: a 60-minute grace matches the confirmation tolerance.
     */
    fun overdue(config: ScheduleConfig, events: List<ScheduledEvent>, logs: List<EventLog>, now: LocalDateTime): OverdueInfo? {
        val pending = nextPending(events, logs) ?: return null
        if (pending.plannedAt >= now) return null
        val delay = between(pending.plannedAt, now)
        val breached = when (pending.kind) {
            EventKind.REMOVE, EventKind.SWAP -> between(pending.cycleStart, now) > ScheduleConfig.SAFE_MAX_WEAR.days + grace
            // The very first ring ends no break; a new ring after "pause already over" ends the one logged before it.
            EventKind.INSERT -> if (pending.cycleIndex == ScheduleEngine.FIRST_INSERT_CYCLE) {
                breakBreached(events, logs, now)
            } else {
                // By id, the same rule as CycleCalculator / nextPending (D9): a removal carried by Settings > Apply
                // keeps its original plannedAt, which can lie before this cycle's start.
                val removeId = events.firstOrNull { it.kind == EventKind.REMOVE && it.cycleIndex == pending.cycleIndex }?.id
                val removedAt = logs.firstOrNull { it.eventId == removeId }?.confirmedAt
                    ?: config.removeTimeFor(pending.cycleStart)
                pauseBreached(removedAt, now)
            }
        }
        return OverdueInfo(pending, delay, breached)
    }

    /** The pause safety threshold: a break from [removedAt] to [at] longer than [ScheduleConfig.SAFE_MAX_PAUSE] days plus the grace. */
    fun pauseBreached(removedAt: LocalDateTime, at: LocalDateTime): Boolean =
        between(removedAt, at) > ScheduleConfig.SAFE_MAX_PAUSE.days + grace

    /**
     * The removal logged before a pending first insertion: the real removal of a schedule restarted after "pause
     * already over". Null for a genuine first ring (nothing logged before it) and once that insertion is logged.
     */
    fun removalBeforeStart(events: List<ScheduledEvent>, logs: List<EventLog>): LocalDateTime? {
        val first = events.firstOrNull { it.cycleIndex == ScheduleEngine.FIRST_INSERT_CYCLE } ?: return null
        if (logs.any { it.eventId == first.id }) return null
        return logs.filter { it.kind == EventKind.REMOVE && it.confirmedAt <= first.plannedAt }.maxOfOrNull { it.confirmedAt }
    }

    /** While a new ring is still to go in, the break since the logged removal already exceeds the safety threshold. */
    fun breakBreached(events: List<ScheduledEvent>, logs: List<EventLog>, now: LocalDateTime): Boolean =
        removalBeforeStart(events, logs)?.let { pauseBreached(it, now) } == true

    private val grace: Duration = ConfirmAction.CONFIRM_TOLERANCE_MINUTES.minutes

    private fun between(from: LocalDateTime, to: LocalDateTime): Duration {
        val tz = TimeZone.UTC
        return to.toInstant(tz) - from.toInstant(tz)
    }

    private fun ScheduleConfig.removeTimeFor(cycleStart: LocalDateTime): LocalDateTime =
        cycleStart.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
}
