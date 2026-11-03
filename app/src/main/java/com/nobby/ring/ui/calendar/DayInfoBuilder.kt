package com.nobby.ring.ui.calendar

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class DayPhase { WEAR, PAUSE, NONE }

data class DayInfo(
    val date: LocalDate,
    val phase: DayPhase,
    val event: EventKind?,
    val hasNote: Boolean,
    val isToday: Boolean,
    /** 1-based day within the wear or pause phase; 0 when [phase] is NONE. */
    val dayInPhase: Int = 0,
    val phaseLength: Int = 0,
)

object DayInfoBuilder {
    fun build(state: ScheduleState, year: Int, month: Int, notes: Set<LocalDate>): List<DayInfo> {
        val first = LocalDate(year, month, 1)
        val length = first.plus(1, DateTimeUnit.MONTH).toEpochDays() - first.toEpochDays()
        val method = state.config.method
        val eventsByDate = state.events.associateBy({ it.plannedAt.date }, { it.kind })
        // pause windows: [REMOVE date, INSERT date)
        val pauseWindows = state.events.filter { it.kind == EventKind.REMOVE }.map { remove ->
            val insert = state.events.firstOrNull { it.kind == EventKind.INSERT && it.plannedAt > remove.plannedAt }
            remove.plannedAt.date to (insert?.plannedAt?.date ?: remove.plannedAt.date.plus(method.pauseDays, DateTimeUnit.DAY))
        }
        val anchorDate = state.config.anchor.date
        val horizonEnd = state.events.lastOrNull()?.plannedAt?.date ?: anchorDate
        val cycleStarts = (listOf(anchorDate) + state.events.filter { it.startsCycle }.map { it.plannedAt.date }).sorted()
        return (0 until length).map { i ->
            val date = first.plus(i, DateTimeUnit.DAY)
            val pauseStart = pauseWindows.firstOrNull { (from, to) -> date >= from && date < to }?.first
            val phase = when {
                date < anchorDate || date > horizonEnd -> DayPhase.NONE
                pauseStart != null -> DayPhase.PAUSE
                else -> DayPhase.WEAR
            }
            val dayInPhase = when (phase) {
                DayPhase.WEAR -> date.toEpochDays() - cycleStarts.last { it <= date }.toEpochDays() + 1
                DayPhase.PAUSE -> date.toEpochDays() - requireNotNull(pauseStart).toEpochDays() + 1
                DayPhase.NONE -> 0
            }
            val phaseLength = when (phase) {
                DayPhase.WEAR -> method.wearDays
                DayPhase.PAUSE -> method.pauseDays
                DayPhase.NONE -> 0
            }
            DayInfo(date, phase, eventsByDate[date], date in notes, date == state.now.date, dayInPhase, phaseLength)
        }
    }
}
