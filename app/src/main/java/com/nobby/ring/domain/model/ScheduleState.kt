package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration

data class CyclePosition(
    val phase: PhaseKind,
    val dayInCycle: Int,
    val dayInPhase: Int,
    val cycleLengthDays: Int,
    val phaseLengthDays: Int,
    val ringIn: Boolean,
    /** False while the first ring has not gone in yet ([ScheduleConfig.firstInsertPending]). */
    val started: Boolean = true,
)

data class OverdueInfo(
    val event: ScheduledEvent,
    val delay: Duration,
    val safetyBreached: Boolean,
)

data class ScheduleState(
    val config: ScheduleConfig,
    val events: List<ScheduledEvent>,
    val logs: List<EventLog>,
    val now: LocalDateTime,
    val position: CyclePosition,
    val nextEvent: ScheduledEvent?,
    val overdue: OverdueInfo?,
) {
    val loggedIds: Set<String> get() = logs.map { it.eventId }.toSet()
    val pendingEvents: List<ScheduledEvent> get() = events.filter { it.id !in loggedIds }
    /** Whole days from today's date to the next event's date (negative when overdue). */
    val daysUntilNext: Int? get() = nextEvent?.let { it.plannedAt.date.toEpochDays() - now.date.toEpochDays() }
}
