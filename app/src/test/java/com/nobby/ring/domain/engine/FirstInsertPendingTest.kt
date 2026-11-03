package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Not started yet": the schedule waits for a first INSERT at the (future) anchor. */
class FirstInsertPendingTest {
    private val start = LocalDateTime(2026, 11, 6, 21, 0)
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), start, horizonMonths = 3, firstInsertPending = true)
    private val before = LocalDateTime(2026, 11, 3, 10, 0)
    private val startId = eventId(EventKind.INSERT, start)

    @Test
    fun `engine starts with the first insertion at the anchor and no removal before it`() {
        val events = ScheduleEngine.generate(config)
        assertEquals(EventKind.INSERT, events[0].kind)
        assertEquals(start, events[0].plannedAt)
        assertEquals(startId, events[0].id)
        assertTrue(events[0].cycleIndex < 0)
        assertEquals(EventKind.REMOVE, events[1].kind)
        assertEquals(LocalDateTime(2026, 11, 27, 21, 0), events[1].plannedAt)
        assertEquals(0, events[1].cycleIndex)
        assertTrue(events.none { it.kind == EventKind.REMOVE && it.plannedAt <= start })
    }

    @Test
    fun `without the flag the engine is unchanged`() {
        val events = ScheduleEngine.generate(config.copy(firstInsertPending = false))
        assertEquals(EventKind.REMOVE, events[0].kind)
        assertEquals(0, events[0].cycleIndex)
    }

    @Test
    fun `before the start the ring is out and the cycle has not started`() {
        val p = CycleCalculator.positionAt(config, emptyList(), before)
        assertFalse(p.ringIn)
        assertFalse(p.started)
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertEquals(1, p.dayInPhase)
    }

    @Test
    fun `once the first insertion is logged the wear phase begins`() {
        val logs = listOf(EventLog(startId, EventKind.INSERT, start, start))
        val p = CycleCalculator.positionAt(config, logs, LocalDateTime(2026, 11, 7, 10, 0))
        assertTrue(p.ringIn)
        assertTrue(p.started)
        assertEquals(PhaseKind.WEAR, p.phase)
    }

    @Test
    fun `nothing is overdue before the start and the next event is the first insertion`() {
        val events = ScheduleEngine.generate(config)
        assertNull(OverdueCalculator.overdue(config, events, emptyList(), before))
        assertEquals(startId, OverdueCalculator.nextPending(events, emptyList())?.id)
    }

    @Test
    fun `a missed first insertion is overdue without a pause safety breach`() {
        val events = ScheduleEngine.generate(config)
        val o = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2027, 1, 5, 21, 0))!! // 60 days late: still no pause breach
        assertEquals(startId, o.event.id)
        assertFalse(o.safetyBreached)
    }

    @Test
    fun `confirming the first insertion starts the schedule there`() {
        val event = ScheduleEngine.generate(config).first()
        val r = ConfirmAction.confirm(config, event, emptyList(), start, ShiftPolicy.KEEP)
        assertFalse(r.config.firstInsertPending)
        assertEquals(start, r.config.anchor)
        assertEquals(EventKind.REMOVE, ScheduleEngine.generate(r.config).first().kind)
        assertEquals(PhaseKind.WEAR, CycleCalculator.positionAt(r.config, r.logs, LocalDateTime(2026, 11, 7, 10, 0)).phase)
    }

    @Test
    fun `an early first insertion re-anchors at the actual time`() {
        val event = ScheduleEngine.generate(config).first()
        val early = LocalDateTime(2026, 11, 5, 8, 0)
        val r = ConfirmAction.confirm(config, event, emptyList(), early, ShiftPolicy.KEEP)
        assertEquals(early, r.config.anchor)
        assertFalse(r.config.firstInsertPending)
    }

    @Test
    fun `reminders are planned for the first insertion and no removal comes before it`() {
        val planned = ReminderPlanner.plan(ReminderRule.defaults(LocalTime(21, 0)), ScheduleEngine.generate(config), emptyList(), before)
        assertEquals(startId, planned.first().eventId)
        assertEquals(EventKind.INSERT, planned.first().kind)
        assertTrue(planned.filter { it.kind == EventKind.REMOVE }.all { it.fireAt > start })
    }

    @Test
    fun `the cycle-0 insert used by the removal bound is not the first insertion`() {
        // removeShiftDays looks up the INSERT with cycleIndex 0; the first insertion must not be mistaken for it.
        val insert = ScheduleEngine.generate(config).first { it.kind == EventKind.INSERT && it.cycleIndex == 0 }
        assertEquals(LocalDateTime(2026, 12, 4, 21, 0), insert.plannedAt)
    }
}
