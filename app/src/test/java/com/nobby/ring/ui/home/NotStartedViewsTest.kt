package com.nobby.ring.ui.home

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.ui.calendar.DayInfoBuilder
import com.nobby.ring.ui.calendar.DayPhase
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.ring.RingLabel
import com.nobby.ring.ui.ring.RingModel
import com.nobby.ring.widget.WidgetModel
import com.nobby.ring.widget.WidgetStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Home, calendar and widget for "not started yet": a countdown to the first insertion, never a wear day or overdue. */
class NotStartedViewsTest {
    private val start = LocalDateTime(2026, 11, 6, 21, 0)
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), start, horizonMonths = 3, firstInsertPending = true)
    private val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 3, 10, 0))

    @Test
    fun `the chip says not started instead of a wear day`() = assertEquals(PhaseChipModel.NotStarted, PhaseChipModel.from(state))

    @Test
    fun `the ring counts down to the first insertion in pause colours`() {
        val ring = RingModel.from(state)
        assertEquals(RingLabel.UNTIL_INSERT, ring.label)
        assertEquals(3, ring.number)
        assertEquals(RingTone.PAUSE, ring.tone)
        assertFalse(ring.safety)
    }

    @Test
    fun `on the start day the ring says today`() {
        val today = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 6, 9, 0))
        assertEquals(RingLabel.TODAY, RingModel.from(today).label)
        assertNull(today.overdue)
    }

    @Test
    fun `the calendar has no phase before the start and the insertion on the start day`() {
        val days = DayInfoBuilder.build(state, 2026, 11, emptySet())
        assertEquals(DayPhase.NONE, days.first { it.date == LocalDate(2026, 11, 3) }.phase)
        assertNull(days.first { it.date == LocalDate(2026, 11, 3) }.card)
        assertEquals(EventKind.INSERT, days.first { it.date == LocalDate(2026, 11, 6) }.event)
        assertEquals(EventKind.REMOVE, days.first { it.date == LocalDate(2026, 11, 27) }.event)
    }

    @Test
    fun `the widget counts down to the insertion and is not urgent`() {
        val s = WidgetStrings(
            "next", "remove", "insert", "overdue", "day", "none",
            inDays = { "in $it" }, overdueDays = { "-$it d" }, overdueHours = { "-$it h" },
            cycleDayFmt = { a, b -> "$a/$b" }, kindLabel = { it.name },
            ringLabel = { l, n -> "$l $n" },
        )
        val w = WidgetModel.build(state, WidgetConfig(), s)
        assertEquals("3", w.headline!!.number)
        assertEquals("UNTIL_INSERT 3", w.headline!!.label)
        assertEquals(RingTone.PAUSE, w.tone)
        assertEquals("in 3", w.rows.first().value)
        assertEquals(start, w.nextAt)
    }
}

/** "Pause already over" with a break of more than 7 days: Home warns before the new ring is due. */
class RestartAfterLongBreakViewsTest {
    private val removedAt = LocalDateTime(2026, 10, 24, 9, 0)
    private val removal = com.nobby.ring.domain.model.EventLog(
        com.nobby.ring.domain.model.eventId(EventKind.REMOVE, removedAt), EventKind.REMOVE, removedAt, removedAt,
    )
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 3, 21, 0), horizonMonths = 3, firstInsertPending = true)

    @Test
    fun `the ring shows the protection notice while the new ring is still ahead`() {
        val state = ScheduleRepository.buildState(config, listOf(removal), LocalDateTime(2026, 11, 3, 10, 0))
        assertNull(state.overdue)
        val ring = RingModel.from(state)
        org.junit.Assert.assertTrue(ring.safety)
        assertEquals(RingLabel.TODAY, ring.label)
    }

    @Test
    fun `a short break shows no notice`() {
        val recent = LocalDateTime(2026, 10, 31, 21, 0)
        val log = removal.copy(eventId = com.nobby.ring.domain.model.eventId(EventKind.REMOVE, recent), plannedAt = recent, confirmedAt = recent)
        assertFalse(RingModel.from(ScheduleRepository.buildState(config, listOf(log), LocalDateTime(2026, 11, 3, 10, 0))).safety)
    }
}
