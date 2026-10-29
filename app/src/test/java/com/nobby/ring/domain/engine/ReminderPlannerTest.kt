package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2)
    private val events = ScheduleEngine.generate(config)
    private val now = LocalDateTime(2026, 11, 2, 12, 0)
    private val dayBefore = ReminderRule(1, ReminderTarget.ANY, 1, LocalTime(20, 0), null, Importance.NORMAL, true)
    private val twoHours = ReminderRule(2, ReminderTarget.REMOVE, 0, null, 2, Importance.HIGH, true)

    @Test
    fun `day-before rule fires the evening before each event`() {
        val plan = ReminderPlanner.plan(listOf(dayBefore), events, emptyList(), now)
        assertEquals(LocalDateTime(2026, 11, 21, 20, 0), plan[0].fireAt)
        assertEquals("REMOVE@2026-11-22T21:00", plan[0].eventId)
        assertEquals(LocalDateTime(2026, 11, 28, 20, 0), plan[1].fireAt)
    }

    @Test
    fun `hours-before rule only targets matching kind`() {
        val plan = ReminderPlanner.plan(listOf(twoHours), events, emptyList(), now)
        assertTrue(plan.all { it.kind == EventKind.REMOVE })
        assertEquals(LocalDateTime(2026, 11, 22, 19, 0), plan[0].fireAt)
        assertEquals(Importance.HIGH, plan[0].importance)
    }

    @Test
    fun `disabled rules and past reminders are skipped`() {
        val plan = ReminderPlanner.plan(listOf(dayBefore.copy(enabled = false)), events, emptyList(), now)
        assertTrue(plan.isEmpty())
        val late = ReminderPlanner.plan(listOf(dayBefore), events, emptyList(), LocalDateTime(2026, 11, 21, 21, 0))
        assertEquals(LocalDateTime(2026, 11, 28, 20, 0), late[0].fireAt)
    }

    @Test
    fun `confirmed events get no reminders`() {
        val log = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), now)
        val plan = ReminderPlanner.plan(listOf(dayBefore), events, listOf(log), now)
        assertEquals("INSERT@2026-11-29T21:00", plan[0].eventId)
    }

    @Test
    fun `swap matches remove and insert targets`() {
        val cont = ScheduleConfig(Method.ring(28, 0, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2)
        val plan = ReminderPlanner.plan(listOf(twoHours), ScheduleEngine.generate(cont), emptyList(), now)
        assertEquals(EventKind.SWAP, plan[0].kind)
    }

    @Test
    fun `result is sorted and limited`() {
        val plan = ReminderPlanner.plan(listOf(dayBefore, twoHours), events, emptyList(), now, limit = 3)
        assertEquals(3, plan.size)
        assertEquals(plan, plan.sortedBy { it.fireAt })
    }
}
