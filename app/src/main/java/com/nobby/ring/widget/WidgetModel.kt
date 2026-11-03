package com.nobby.ring.widget

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.OverdueInfo
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.ring.OverdueAmount
import com.nobby.ring.ui.ring.RingLabel
import com.nobby.ring.ui.ring.RingModel
import kotlinx.datetime.LocalDateTime

data class WidgetStrings(
    val nextAny: String, val nextRemove: String, val nextInsert: String, val overdue: String, val cycleDay: String, val none: String,
    val inDays: (Int) -> String, val overdueDays: (Int) -> String, val overdueHours: (Int) -> String,
    val cycleDayFmt: (Int, Int) -> String, val kindLabel: (EventKind) -> String,
    val ringLabel: (RingLabel, Int) -> String = { _, _ -> "" },
    val today: String = "",
    val dayOf: (Int, Int) -> String = { _, _ -> "" },
)

data class WidgetRowData(val row: WidgetRow, val label: String, val value: String, val urgent: Boolean)
data class WidgetHeadline(val number: String, val label: String)
data class WidgetData(
    val rows: List<WidgetRowData>,
    val dueEventId: String?,
    val headline: WidgetHeadline? = null,
    val progress: Float = 0f,
    val mood: MascotMood = MascotMood.HAPPY,
    val tone: RingTone = RingTone.WEAR,
    val dayLabel: String = "",
    /** Home ring beads (phase days) and done beads, from [RingModel]. */
    val beads: Int = 1,
    val done: Int = 0,
    /** Planned time of the event the ring counts down to (or the overdue one); null when nothing is pending. */
    val nextAt: LocalDateTime? = null,
)

internal fun WidgetData.head(): WidgetHeadline = headline ?: WidgetHeadline("–", "")
internal fun WidgetData.urgent(): Boolean = tone == RingTone.OVERDUE || tone == RingTone.SAFETY

object WidgetModel {
    fun build(state: ScheduleState?, config: WidgetConfig, s: WidgetStrings): WidgetData {
        if (state == null) return WidgetData(listOf(WidgetRowData(WidgetRow.NEXT_ANY, s.nextAny, s.none, false)), null, WidgetHeadline("–", s.none))
        val due = state.nextEvent?.takeIf { state.daysUntilNext != null && state.daysUntilNext!! <= 0 }?.id
        val rows = when (config.mode) {
            WidgetMode.SINGLE -> listOf(WidgetRow.NEXT_ANY)
            WidgetMode.MULTI -> config.rows.take(WidgetConfig.MAX_ROWS).ifEmpty { listOf(WidgetRow.NEXT_ANY) }
        }
        val ring = RingModel.from(state)
        val p = state.position
        val length = p.phaseLengthDays.coerceAtLeast(1)
        val head = headline(ring, s)
        return WidgetData(
            rows = rows.map { row(it, state, s) },
            dueEventId = due,
            headline = head,
            progress = (p.dayInPhase.toFloat() / length).coerceIn(0f, 1f),
            mood = ring.mood,
            tone = ring.tone,
            beads = ring.beads,
            done = ring.done,
            nextAt = ring.event?.plannedAt,
            // While overdue a full "Day 21/21" is misleading and the big number needs its unit: name it here, as Home does.
            dayLabel = if (state.overdue != null) head.label else s.dayOf(p.dayInPhase.coerceAtMost(length), p.phaseLengthDays),
        )
    }

    private fun headline(ring: RingModel, s: WidgetStrings): WidgetHeadline = when (ring.label) {
        RingLabel.TODAY -> WidgetHeadline(s.today, ring.event?.let { s.kindLabel(it.kind) } ?: s.none)
        RingLabel.NONE -> WidgetHeadline("–", s.none)
        else -> WidgetHeadline("${ring.number ?: 0}", s.ringLabel(ring.label, ring.number ?: 0))
    }

    private fun row(row: WidgetRow, state: ScheduleState, s: WidgetStrings): WidgetRowData = when (row) {
        WidgetRow.NEXT_ANY -> countdown(row, state.nextEvent, state, s, labelOverride = null)
        WidgetRow.NEXT_REMOVE -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.INSERT }, state, s, s.nextRemove)
        WidgetRow.NEXT_INSERT -> countdown(row, state.pendingEvents.firstOrNull { it.kind != EventKind.REMOVE }, state, s, s.nextInsert)
        WidgetRow.OVERDUE -> {
            val o = state.overdue
            if (o == null) WidgetRowData(row, s.overdue, "–", false)
            else WidgetRowData(row, s.overdue, overdueText(o, s), true)
        }
        WidgetRow.CYCLE_DAY -> WidgetRowData(row, s.cycleDay, s.cycleDayFmt(state.position.dayInCycle, state.position.cycleLengthDays), false)
    }

    private fun countdown(row: WidgetRow, event: ScheduledEvent?, state: ScheduleState, s: WidgetStrings, labelOverride: String?): WidgetRowData {
        if (event == null) return WidgetRowData(row, labelOverride ?: s.nextAny, s.none, false)
        val days = daysUntil(event, state)
        val label = labelOverride ?: s.kindLabel(event.kind)
        val overdue = state.overdue?.takeIf { it.event.id == event.id }
        if (overdue != null) return WidgetRowData(row, label, overdueText(overdue, s), true)
        return if (days < 0) WidgetRowData(row, label, s.overdueDays(-days), true) else WidgetRowData(row, label, s.inDays(days), false)
    }

    /** Same rule as the Home ring and the Single headline: [OverdueAmount]. */
    private fun overdueText(o: OverdueInfo, s: WidgetStrings): String = when (val amount = OverdueAmount.of(o.delay)) {
        is OverdueAmount.Hours -> s.overdueHours(amount.hours)
        is OverdueAmount.Days -> s.overdueDays(amount.days)
    }

    private fun daysUntil(event: ScheduledEvent, state: ScheduleState): Int =
        event.plannedAt.date.toEpochDays() - state.now.date.toEpochDays()
}
