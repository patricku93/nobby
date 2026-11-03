package com.nobby.ring.widget

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Waiting for the first insertion: the day label and the cycle-day row say when it starts, never "day 1". */
class WidgetNotStartedTest {
    private val strings = WidgetStrings(
        nextAny = "Next", nextRemove = "Remove", nextInsert = "Insert", overdue = "Overdue", cycleDay = "Cycle", none = "All done",
        inDays = { "in $it d" }, overdueDays = { "$it d overdue" }, overdueHours = { "$it h overdue" }, cycleDayFmt = { d, n -> "Day $d of $n" },
        kindLabel = { it.name.lowercase() }, ringLabel = { l, n -> "$l $n" }, today = "Today", dayOf = { d, n -> "Day $d/$n" },
        startsIn = { "Starts in $it d" }, startsToday = "Starts today", notStarted = "Not started",
        ringCd = { n, l -> "$n $l" }, ringCdToday = { t, k -> "$t: $k" }, withDetail = { a, b -> "$a, $b" },
    )
    private val start = LocalDateTime(2026, 11, 6, 21, 0)
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), start, horizonMonths = 3, firstInsertPending = true)
    private fun at(now: LocalDateTime) = ScheduleRepository.buildState(config, emptyList(), now)
    private val multi = WidgetConfig(WidgetMode.MULTI, listOf(WidgetRow.NEXT_ANY, WidgetRow.CYCLE_DAY))

    @Test
    fun `three days before the start the day label and the cycle row count down to it`() {
        val w = WidgetModel.build(at(LocalDateTime(2026, 11, 3, 10, 0)), multi, strings)
        assertEquals("Starts in 3 d", w.dayLabel)
        assertEquals("Starts in 3 d", w.rows.first { it.row == WidgetRow.CYCLE_DAY }.value)
        assertEquals(0f, w.progress)
        assertTrue(w.description, w.description.endsWith(", Not started"))
    }

    @Test
    fun `on the start day it says it starts today`() {
        val w = WidgetModel.build(at(LocalDateTime(2026, 11, 6, 9, 0)), multi, strings)
        assertEquals("Starts today", w.dayLabel)
        assertEquals("Starts today", w.rows.first { it.row == WidgetRow.CYCLE_DAY }.value)
    }

    @Test
    fun `once started the usual phase day comes back`() {
        val started = ScheduleRepository.buildState(config.copy(firstInsertPending = false), emptyList(), LocalDateTime(2026, 11, 8, 10, 0))
        val w = WidgetModel.build(started, multi, strings)
        assertEquals("Day 3/21", w.dayLabel)
        assertEquals("Day 3 of 28", w.rows.first { it.row == WidgetRow.CYCLE_DAY }.value)
    }
}
