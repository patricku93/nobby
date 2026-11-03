package com.nobby.ring.widget

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val strings = WidgetStrings(
        nextAny = "Next", nextRemove = "Remove", nextInsert = "Insert", overdue = "Overdue", cycleDay = "Cycle", none = "All done",
        inDays = { "in $it d" }, overdueDays = { "$it d overdue" }, overdueHours = { "$it h overdue" }, cycleDayFmt = { d, n -> "Day $d of $n" },
        kindLabel = { it.name.lowercase() },
    )
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2)

    @Test
    fun `single mode shows the next event countdown`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 12, 12, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), strings)
        assertEquals(1, data.rows.size)
        assertEquals("remove", data.rows[0].label)
        assertEquals("in 10 d", data.rows[0].value)
        assertNull(data.dueEventId)
    }

    @Test
    fun `multi mode shows configured rows and overdue is urgent`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 24, 12, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.MULTI, listOf(WidgetRow.NEXT_INSERT, WidgetRow.OVERDUE, WidgetRow.CYCLE_DAY)), strings)
        assertEquals(3, data.rows.size)
        assertEquals("in 5 d", data.rows[0].value)
        assertEquals("1 d overdue", data.rows[1].value) // 39 h late: one whole day, not two calendar days
        assertTrue(data.rows[1].urgent)
        assertEquals("Day 24 of 28", data.rows[2].value)
        assertEquals("REMOVE@2026-11-22T21:00", data.dueEventId)
    }

    @Test
    fun `null state yields the none row`() {
        val data = WidgetModel.build(null, WidgetConfig(), strings)
        assertEquals("All done", data.rows[0].value)
    }

    @Test
    fun `overdue by less than a day shows hours`() {
        // REMOVE planned 2026-11-22 21:00, now 5 h later (next calendar day)
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 23, 2, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.MULTI, listOf(WidgetRow.NEXT_ANY, WidgetRow.OVERDUE)), strings)
        assertEquals("5 h overdue", data.rows[0].value)
        assertTrue(data.rows[0].urgent)
        assertEquals("5 h overdue", data.rows[1].value)
        assertTrue(data.rows[1].urgent)
    }

    @Test
    fun `overdue on the same day shows hours in single mode`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 22, 23, 30))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), strings)
        assertEquals("2 h overdue", data.rows[0].value)
        assertTrue(data.rows[0].urgent)
    }

    private val v2 = strings.copy(ringLabel = { label, n -> "$label $n" }, today = "Today", dayOf = { d, n -> "$d/$n" })

    @Test
    fun `headline, progress and mood come from the ring model`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 12, 12, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), v2)
        assertEquals(WidgetHeadline("10", "UNTIL_REMOVE 10"), data.headline)
        assertEquals(12f / 21f, data.progress, 0.0001f)
        assertEquals(MascotMood.HAPPY, data.mood)
        assertEquals(RingTone.WEAR, data.tone)
        assertEquals("12/21", data.dayLabel)
    }

    @Test
    fun `due today shows the word, the event and the done target`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 22, 10, 0))
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), v2)
        assertEquals(WidgetHeadline("Today", "remove"), data.headline)
        assertEquals(RingTone.DUE, data.tone)
        assertEquals(MascotMood.NEUTRAL, data.mood)
        assertEquals("REMOVE@2026-11-22T21:00", data.dueEventId)
        assertEquals("21/21", data.dayLabel)
    }

    @Test
    fun `single headline and multi rows agree on hours across midnight`() {
        // Event at 22:00, now 01:00: every surface says 3 hours, none says "1 day".
        val late = ScheduleConfig(Method.ring(21, 7, LocalTime(22, 0)), LocalDateTime(2026, 11, 1, 22, 0), 2)
        val state = ScheduleRepository.buildState(late, emptyList(), LocalDateTime(2026, 11, 23, 1, 0))
        val single = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), v2)
        val multi = WidgetModel.build(state, WidgetConfig(WidgetMode.MULTI, listOf(WidgetRow.NEXT_ANY, WidgetRow.OVERDUE)), v2)
        assertEquals(WidgetHeadline("3", "OVERDUE_HOURS 3"), single.headline)
        assertEquals("3 h overdue", single.rows[0].value)
        assertEquals("3 h overdue", multi.rows[0].value)
        assertEquals("3 h overdue", multi.rows[1].value)
    }

    @Test
    fun `overdue names the unit in the day label instead of a full phase (D10)`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 25, 12, 0)) // REMOVE 2 d 15 h late
        val data = WidgetModel.build(state, WidgetConfig(WidgetMode.SINGLE, emptyList()), v2)
        assertEquals(WidgetHeadline("2", "OVERDUE_DAYS 2"), data.headline)
        assertEquals("OVERDUE_DAYS 2", data.dayLabel)
        assertEquals("REMOVE@2026-11-22T21:00", data.dueEventId)
    }
}
