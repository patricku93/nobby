package com.nobby.ring.widget

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val strings = WidgetStrings(
        nextAny = "Next", nextRemove = "Remove", nextInsert = "Insert", overdue = "Overdue", cycleDay = "Cycle", none = "All done",
        inDays = { "in $it d" }, overdueDays = { "$it d overdue" }, cycleDayFmt = { d, n -> "Day $d of $n" },
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
        assertEquals("2 d overdue", data.rows[1].value)
        assertTrue(data.rows[1].urgent)
        assertEquals("Day 24 of 28", data.rows[2].value)
        assertEquals("REMOVE@2026-11-22T21:00", data.dueEventId)
    }

    @Test
    fun `null state yields the none row`() {
        val data = WidgetModel.build(null, WidgetConfig(), strings)
        assertEquals("All done", data.rows[0].value)
    }
}
