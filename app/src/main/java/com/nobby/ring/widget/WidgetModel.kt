package com.nobby.ring.widget

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.OverdueInfo
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import kotlin.time.Duration.Companion.hours

data class WidgetStrings(
    val nextAny: String, val nextRemove: String, val nextInsert: String, val overdue: String, val cycleDay: String, val none: String,
    val inDays: (Int) -> String, val overdueDays: (Int) -> String, val overdueHours: (Int) -> String,
    val cycleDayFmt: (Int, Int) -> String, val kindLabel: (EventKind) -> String,
)

data class WidgetRowData(val row: WidgetRow, val label: String, val value: String, val urgent: Boolean)
data class WidgetData(val rows: List<WidgetRowData>, val dueEventId: String?)

object WidgetModel {
    fun build(state: ScheduleState?, config: WidgetConfig, s: WidgetStrings): WidgetData {
        if (state == null) return WidgetData(listOf(WidgetRowData(WidgetRow.NEXT_ANY, s.nextAny, s.none, false)), null)
        val due = state.nextEvent?.takeIf { state.daysUntilNext != null && state.daysUntilNext!! <= 0 }?.id
        val rows = when (config.mode) {
            WidgetMode.SINGLE -> listOf(WidgetRow.NEXT_ANY)
            WidgetMode.MULTI -> config.rows.take(WidgetConfig.MAX_ROWS).ifEmpty { listOf(WidgetRow.NEXT_ANY) }
        }
        return WidgetData(rows.map { row(it, state, s) }, due)
    }

    private fun row(row: WidgetRow, state: ScheduleState, s: WidgetStrings): WidgetRowData = when (row) {
        WidgetRow.NEXT_ANY -> countdown(row, state.nextEvent, state, s, labelOverride = null)
        WidgetRow.NEXT_REMOVE -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.INSERT }, state, s, s.nextRemove)
        WidgetRow.NEXT_INSERT -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.REMOVE }, state, s, s.nextInsert)
        WidgetRow.OVERDUE -> {
            val o = state.overdue
            if (o == null) WidgetRowData(row, s.overdue, "–", false)
            else WidgetRowData(row, s.overdue, overdueText(o, state, s), true)
        }
        WidgetRow.CYCLE_DAY -> WidgetRowData(row, s.cycleDay, s.cycleDayFmt(state.position.dayInCycle, state.position.cycleLengthDays), false)
    }

    private fun countdown(row: WidgetRow, event: ScheduledEvent?, state: ScheduleState, s: WidgetStrings, labelOverride: String?): WidgetRowData {
        if (event == null) return WidgetRowData(row, labelOverride ?: s.nextAny, s.none, false)
        val days = daysUntil(event, state)
        val label = labelOverride ?: s.kindLabel(event.kind)
        val overdue = state.overdue?.takeIf { it.event.id == event.id }
        if (overdue != null) return WidgetRowData(row, label, overdueText(overdue, state, s), true)
        return if (days < 0) WidgetRowData(row, label, s.overdueDays(-days), true) else WidgetRowData(row, label, s.inDays(days), false)
    }

    /** Under one day the overdue time is shown in hours (at least 1), otherwise in calendar days. */
    private fun overdueText(o: OverdueInfo, state: ScheduleState, s: WidgetStrings): String =
        if (o.delay < 24.hours) s.overdueHours(o.delay.inWholeHours.toInt().coerceAtLeast(1))
        else s.overdueDays(-daysUntil(o.event, state))

    private fun daysUntil(event: ScheduledEvent, state: ScheduleState): Int =
        event.plannedAt.date.toEpochDays() - state.now.date.toEpochDays()
}
