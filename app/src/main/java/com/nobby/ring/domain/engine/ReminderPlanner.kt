package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.PlannedReminder
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

object ReminderPlanner {

    fun plan(
        rules: List<ReminderRule>,
        events: List<ScheduledEvent>,
        logs: List<EventLog>,
        now: LocalDateTime,
        limit: Int = 50,
    ): List<PlannedReminder> {
        val logged = logs.map { it.eventId }.toSet()
        val pending = events.filter { it.id !in logged }
        return rules.asSequence()
            .filter { it.enabled }
            .flatMap { rule ->
                pending.asSequence()
                    .filter { rule.matches(it.kind) }
                    .map { event -> PlannedReminder(rule.id, event.id, fireAt(rule, event.plannedAt), rule.importance, event.kind) }
            }
            .filter { it.fireAt > now }
            .sortedBy { it.fireAt }
            .take(limit)
            .toList()
    }

    private fun fireAt(rule: ReminderRule, plannedAt: LocalDateTime): LocalDateTime {
        val hours = rule.offsetHours
        if (hours != null) {
            val tz = TimeZone.UTC
            return (plannedAt.toInstant(tz) - hours.hours).toLocalDateTime(tz)
        }
        val date = plannedAt.date.minus(rule.offsetDays, DateTimeUnit.DAY)
        return date.atTime(rule.timeOfDay ?: plannedAt.time)
    }
}
