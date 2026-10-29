package com.nobby.ring.ui.calendar

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class DayPhase { WEAR, PAUSE, NONE }

data class DayInfo(val date: LocalDate, val phase: DayPhase, val event: EventKind?, val hasNote: Boolean, val isToday: Boolean)

object DayInfoBuilder {
    fun build(state: ScheduleState, year: Int, month: Int, notes: Set<LocalDate>): List<DayInfo> {
        val first = LocalDate(year, month, 1)
        val length = first.plus(1, DateTimeUnit.MONTH).toEpochDays() - first.toEpochDays()
        val eventsByDate = state.events.associateBy({ it.plannedAt.date }, { it.kind })
        // pause windows: [REMOVE date, INSERT date)
        val pauseWindows = state.events.filter { it.kind == EventKind.REMOVE }.map { remove ->
            val insert = state.events.firstOrNull { it.kind == EventKind.INSERT && it.plannedAt > remove.plannedAt }
            remove.plannedAt.date to (insert?.plannedAt?.date ?: remove.plannedAt.date.plus(state.config.method.pauseDays, DateTimeUnit.DAY))
        }
        val anchorDate = state.config.anchor.date
        val horizonEnd = state.events.lastOrNull()?.plannedAt?.date ?: anchorDate
        return (0 until length).map { i ->
            val date = first.plus(i, DateTimeUnit.DAY)
            val phase = when {
                date < anchorDate || date > horizonEnd -> DayPhase.NONE
                pauseWindows.any { (from, to) -> date >= from && date < to } -> DayPhase.PAUSE
                else -> DayPhase.WEAR
            }
            DayInfo(date, phase, eventsByDate[date], date in notes, date == state.now.date)
        }
    }
}
