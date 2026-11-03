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

/** "Pause already over": the new ring's insertion follows a removal logged at its real time. */
class RestartAfterBreakTest {
    private val method = Method.ring(21, 7, LocalTime(21, 0))
    private val removedAt = LocalDateTime(2026, 10, 24, 9, 0)
    private val removal = EventLog(eventId(EventKind.REMOVE, removedAt), EventKind.REMOVE, removedAt, removedAt)

    private fun restart(start: LocalDateTime) = ScheduleConfig(method, start, horizonMonths = 3, firstInsertPending = true)

    @Test
    fun `a break already longer than 7 days counts before the new ring is due`() {
        val start = LocalDateTime(2026, 11, 3, 10, 1)
        val config = restart(start)
        val events = ScheduleEngine.generate(config)
        val now = LocalDateTime(2026, 11, 3, 10, 0, 30)
        assertNull(OverdueCalculator.overdue(config, events, listOf(removal), now))
        assertTrue(OverdueCalculator.breakBreached(events, listOf(removal), now))
    }

    @Test
    fun `a missed new ring after a long break is overdue with the pause safety breach`() {
        val config = restart(LocalDateTime(2026, 11, 3, 10, 1))
        val o = OverdueCalculator.overdue(config, ScheduleEngine.generate(config), listOf(removal), LocalDateTime(2026, 11, 3, 12, 0))!!
        assertTrue(o.safetyBreached)
    }

    @Test
    fun `the break is measured from the logged removal, within 7 days nothing is breached`() {
        val recent = LocalDateTime(2026, 10, 30, 21, 0)
        val log = EventLog(eventId(EventKind.REMOVE, recent), EventKind.REMOVE, recent, recent)
        val config = restart(LocalDateTime(2026, 11, 5, 21, 0))
        val events = ScheduleEngine.generate(config)
        assertFalse(OverdueCalculator.breakBreached(events, listOf(log), LocalDateTime(2026, 11, 3, 10, 0)))
        assertFalse(OverdueCalculator.overdue(config, events, listOf(log), LocalDateTime(2026, 11, 6, 21, 0))!!.safetyBreached)
        assertTrue(OverdueCalculator.overdue(config, events, listOf(log), LocalDateTime(2026, 11, 6, 22, 1))!!.safetyBreached)
    }

    @Test
    fun `a genuine first ring has no break to breach`() {
        val config = restart(LocalDateTime(2026, 11, 3, 10, 1))
        assertFalse(OverdueCalculator.breakBreached(ScheduleEngine.generate(config), emptyList(), LocalDateTime(2027, 1, 1, 10, 0)))
    }

    @Test
    fun `once the new ring is in the old break no longer counts`() {
        val start = LocalDateTime(2026, 11, 3, 10, 1)
        val config = restart(start)
        val logs = listOf(removal, EventLog(ScheduleEngine.firstInsertId(config), EventKind.INSERT, start, start))
        assertFalse(OverdueCalculator.breakBreached(ScheduleEngine.generate(config), logs, LocalDateTime(2026, 11, 4, 10, 0)))
    }

    @Test
    fun `the break check matches the overdue rule with its 60-minute grace`() {
        assertFalse(OverdueCalculator.pauseBreached(removedAt, LocalDateTime(2026, 10, 31, 10, 0)))
        assertTrue(OverdueCalculator.pauseBreached(removedAt, LocalDateTime(2026, 10, 31, 10, 1)))
    }
}

/** A first ring confirmed late: there is no rhythm to keep, so a one-tap Done starts the cycle at the real time (M2). */
class LateFirstInsertionTest {
    private val start = LocalDateTime(2026, 11, 6, 21, 0)
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), start, horizonMonths = 3, firstInsertPending = true)
    private val first = ScheduleEngine.generate(config).first()
    private val late = LocalDateTime(2026, 11, 8, 9, 0)

    @Test
    fun `a one-tap Done on a late first insertion shifts the start to the real time`() {
        assertEquals(ShiftPolicy.SHIFT, ConfirmAction.quickConfirmPolicy(first, late))
        val r = ConfirmAction.confirm(config, first, emptyList(), late, ConfirmAction.quickConfirmPolicy(first, late))
        assertEquals(late, r.config.anchor)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), ScheduleEngine.generate(r.config).first().plannedAt) // full 21 days
    }

    @Test
    fun `an on-time one-tap Done keeps the planned start`() {
        val onTime = LocalDateTime(2026, 11, 6, 21, 30)
        val r = ConfirmAction.confirm(config, first, emptyList(), onTime, ConfirmAction.quickConfirmPolicy(first, onTime))
        assertEquals(start, r.config.anchor)
    }

    @Test
    fun `in the dialog KEEP keeps the planned start and SHIFT moves it`() {
        assertEquals(start, ConfirmAction.confirm(config, first, emptyList(), late, ShiftPolicy.KEEP).config.anchor)
        assertEquals(late, ConfirmAction.confirm(config, first, emptyList(), late, ShiftPolicy.SHIFT).config.anchor)
    }

    @Test
    fun `a later insertion still keeps the rhythm on one tap`() {
        val started = config.copy(firstInsertPending = false)
        val insert = ScheduleEngine.generate(started).first { it.kind == EventKind.INSERT }
        assertEquals(ShiftPolicy.KEEP, ConfirmAction.quickConfirmPolicy(insert, LocalDateTime(2026, 12, 6, 9, 0)))
    }
}
