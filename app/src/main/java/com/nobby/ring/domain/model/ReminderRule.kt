package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

enum class Importance { LOW, NORMAL, HIGH, URGENT }

enum class ReminderTarget { REMOVE, INSERT, ANY }

data class ReminderRule(
    val id: Long,
    val target: ReminderTarget,
    val offsetDays: Int,
    val timeOfDay: LocalTime?,
    val offsetHours: Int?,
    val importance: Importance,
    val enabled: Boolean,
) {
    fun matches(kind: EventKind): Boolean = when (target) {
        ReminderTarget.ANY -> true
        ReminderTarget.REMOVE -> kind == EventKind.REMOVE || kind == EventKind.SWAP
        ReminderTarget.INSERT -> kind == EventKind.INSERT || kind == EventKind.SWAP
    }

    companion object {
        fun defaults(timeOfDay: LocalTime): List<ReminderRule> = listOf(
            ReminderRule(0, ReminderTarget.ANY, 1, LocalTime(20, 0), null, Importance.NORMAL, true),
            ReminderRule(0, ReminderTarget.ANY, 0, timeOfDay, null, Importance.HIGH, true),
        )
    }
}

data class OverdueConfig(val repeatEveryHours: Int = 2, val snoozeHours: Int = 1)

data class PlannedReminder(
    val ruleId: Long,
    val eventId: String,
    val fireAt: LocalDateTime,
    val importance: Importance,
    val kind: EventKind,
)
