package com.nobby.ring.domain.engine

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
                // The shifted schedule regenerates this REMOVE under a new id; log it too so it stays confirmed.
                val shiftedRemove = ScheduleEngine.generate(shifted).first()
                ConfirmResult(
                    shifted,
                    logs + eventLog + EventLog(shiftedRemove.id, shiftedRemove.kind, shiftedRemove.plannedAt, confirmedAt),
                )
            }
            else -> ConfirmResult(config, logs + eventLog)
        }
    }
}
