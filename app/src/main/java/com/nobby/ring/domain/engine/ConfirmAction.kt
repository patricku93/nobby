package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

enum class ShiftPolicy { SHIFT, KEEP }

data class ConfirmResult(val config: ScheduleConfig, val logs: List<EventLog>)

object ConfirmAction {
    private val tz = TimeZone.UTC

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
    ): ConfirmResult {
        val eventLog = EventLog(event.id, event.kind, event.plannedAt, confirmedAt)
        return when {
            event.startsCycle -> ConfirmResult(
                config.copy(
                    anchor = if (policy == ShiftPolicy.SHIFT) confirmedAt else event.plannedAt,
                    skipPauseOnce = false,
                ),
                logs + eventLog,
            )
            policy == ShiftPolicy.SHIFT -> {
                val delay = confirmedAt.toInstant(tz) - event.plannedAt.toInstant(tz)
                val shifted = config.copy(anchor = (config.anchor.toInstant(tz) + delay).toLocalDateTime(tz))
                // The shifted schedule regenerates this REMOVE under a new id. The log is keyed by the shifted id
                // so the regenerated schedule sees the removal as confirmed, but it records the originally planned
                // time so history shows one removal with planned vs actual.
                val shiftedRemove = ScheduleEngine.generate(shifted)
                    .firstOrNull { it.kind == EventKind.REMOVE && it.cycleIndex == 0 }
                if (shiftedRemove == null) {
                    // Cannot happen for a valid cycle-0 REMOVE; degrade to KEEP behaviour.
                    ConfirmResult(config, logs + eventLog)
                } else {
                    ConfirmResult(
                        shifted,
                        logs + EventLog(shiftedRemove.id, EventKind.REMOVE, event.plannedAt, confirmedAt),
                    )
                }
            }
            else -> ConfirmResult(config, logs + eventLog)
        }
    }
}
