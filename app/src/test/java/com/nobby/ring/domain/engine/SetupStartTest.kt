package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStartTest {
    private val method = Method.ring(21, 7, LocalTime(21, 0))
    private val now = LocalDateTime(2026, 11, 3, 10, 0, 30)

    private fun ready(plan: SetupPlan) = plan as SetupPlan.Ready

    /** What the repository saves: the plan's config and log rows, written together by ScheduleRepository.startWith. */
    private fun saved(plan: SetupPlan.Ready) = ConfirmResult(plan.config, plan.logs)

    @Test
    fun `ring in anchors at the insertion`() {
        val at = LocalDateTime(2026, 11, 1, 21, 0)
        val plan = ready(SetupStart.plan(SetupStatus.RING_IN, method, at, now))
        assertEquals(at, plan.config.anchor)
        assertFalse(plan.config.firstInsertPending)
        assertTrue(plan.logs.isEmpty())
    }

    @Test
    fun `ring in up to the current minute is accepted, later is rejected`() {
        assertTrue(SetupStart.plan(SetupStatus.RING_IN, method, LocalDateTime(2026, 11, 3, 10, 0), now) is SetupPlan.Ready)
        assertEquals(SetupPlan.InFuture, SetupStart.plan(SetupStatus.RING_IN, method, LocalDateTime(2026, 11, 3, 10, 1), now))
    }

    @Test
    fun `ring in older than the wear days is still accepted`() {
        val at = LocalDateTime(2026, 10, 1, 21, 0)
        assertEquals(at, ready(SetupStart.plan(SetupStatus.RING_IN, method, at, now)).config.anchor)
    }

    @Test
    fun `ring out anchors wear days before the removal and confirms that cycle's removal`() {
        val removedAt = LocalDateTime(2026, 11, 1, 21, 0)
        val plan = ready(SetupStart.plan(SetupStatus.RING_OUT, method, removedAt, now))
        assertEquals(LocalDateTime(2026, 10, 11, 21, 0), plan.config.anchor)
        assertEquals(CycleCalculator.currentRemoveId(plan.config), plan.logs.single().eventId)
        assertEquals(removedAt, plan.logs.single().confirmedAt)

        val result = saved(plan)
        val state = com.nobby.ring.domain.model.ScheduleState(
            result.config, ScheduleEngine.generate(result.config), result.logs, now,
            CycleCalculator.positionAt(result.config, result.logs, now),
            OverdueCalculator.nextPending(ScheduleEngine.generate(result.config), result.logs),
            OverdueCalculator.overdue(result.config, ScheduleEngine.generate(result.config), result.logs, now),
        )
        assertEquals(PhaseKind.PAUSE, state.position.phase)
        assertEquals(EventKind.INSERT, state.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 11, 8, 21, 0), state.nextEvent!!.plannedAt)
        assertNull(state.overdue)
    }

    @Test
    fun `ring out off the routine time keeps the break within the pause like a confirmation`() {
        // Removed at 09:00, routine 21:00: the planned insertion may not be later than removal + 7 days.
        val removedAt = LocalDateTime(2026, 11, 1, 9, 0)
        val result = saved(ready(SetupStart.plan(SetupStatus.RING_OUT, method, removedAt, now)))
        val insert = ScheduleEngine.generate(result.config).first { it.kind == EventKind.INSERT }
        assertTrue(insert.plannedAt <= LocalDateTime(2026, 11, 8, 9, 0))
        assertTrue(insert.plannedAt > now)
        assertTrue(result.logs.single().eventId == CycleCalculator.currentRemoveId(result.config))
    }

    @Test
    fun `ring out in the future is rejected`() {
        assertEquals(SetupPlan.InFuture, SetupStart.plan(SetupStatus.RING_OUT, method, LocalDateTime(2026, 11, 3, 12, 0), now))
    }

    @Test
    fun `ring out whose insertion falls on the current minute is over, not due with its seconds already past`() {
        // I2: the clock has seconds; an insertion at 21:00:00 is already behind 21:00:37, so it would be overdue at once.
        val at = LocalDateTime(2026, 11, 3, 21, 0, 37)
        assertEquals(SetupPlan.PauseOver, SetupStart.plan(SetupStatus.RING_OUT, method, LocalDateTime(2026, 10, 27, 21, 0), at))
    }

    @Test
    fun `ring out one pause ago with the insertion still ahead is a pause`() {
        val at = LocalDateTime(2026, 11, 3, 20, 59, 59)
        val plan = ready(SetupStart.plan(SetupStatus.RING_OUT, method, LocalDateTime(2026, 10, 27, 21, 0), at))
        val insert = ScheduleEngine.generate(saved(plan).config).first { it.kind == EventKind.INSERT }
        assertEquals(LocalDateTime(2026, 11, 3, 21, 0), insert.plannedAt)
    }

    @Test
    fun `ring out longer ago than the pause means the pause is over`() {
        val at = LocalDateTime(2026, 11, 3, 21, 1)
        assertEquals(SetupPlan.PauseOver, SetupStart.plan(SetupStatus.RING_OUT, method, LocalDateTime(2026, 10, 27, 21, 0), at))
        assertEquals(SetupPlan.PauseOver, SetupStart.plan(SetupStatus.RING_OUT, method, LocalDateTime(2026, 10, 20, 21, 0), now))
    }

    @Test
    fun `ring out whose bounded insertion already passed means the pause is over`() {
        // Removed 09:00 six days and 14 hours ago: the bound plans the insertion for last night 21:00, already past.
        val at = LocalDateTime(2026, 11, 7, 23, 0)
        assertEquals(SetupPlan.PauseOver, SetupStart.plan(SetupStatus.RING_OUT, method, LocalDateTime(2026, 11, 1, 9, 0), at))
    }

    @Test
    fun `ring out without a pause in the rhythm means the pause is over`() {
        val continuous = Method.ring(28, 0, LocalTime(21, 0))
        assertEquals(SetupPlan.PauseOver, SetupStart.plan(SetupStatus.RING_OUT, continuous, LocalDateTime(2026, 11, 2, 21, 0), now))
    }

    @Test
    fun `not started waits for the first insertion today or later`() {
        val today = LocalDateTime(2026, 11, 3, 21, 0)
        val plan = ready(SetupStart.plan(SetupStatus.NOT_STARTED, method, today, now))
        assertEquals(today, plan.config.anchor)
        assertTrue(plan.config.firstInsertPending)
        assertTrue(plan.logs.isEmpty())
        val inThree = LocalDateTime(2026, 11, 6, 8, 0)
        assertEquals(inThree, ready(SetupStart.plan(SetupStatus.NOT_STARTED, method, inThree, now)).config.anchor)
    }

    @Test
    fun `not started in the past or at the current minute is rejected, the next minute is fine`() {
        assertEquals(SetupPlan.InPast, SetupStart.plan(SetupStatus.NOT_STARTED, method, LocalDateTime(2026, 11, 3, 9, 59), now))
        assertEquals(SetupPlan.InPast, SetupStart.plan(SetupStatus.NOT_STARTED, method, LocalDateTime(2026, 11, 2, 21, 0), now))
        // I2: 10:00 is already behind 10:00:30, so that start would be overdue right after setup.
        assertEquals(SetupPlan.InPast, SetupStart.plan(SetupStatus.NOT_STARTED, method, LocalDateTime(2026, 11, 3, 10, 0), now))
        assertTrue(SetupStart.plan(SetupStatus.NOT_STARTED, method, LocalDateTime(2026, 11, 3, 10, 1), now) is SetupPlan.Ready)
    }

    @Test
    fun `pause over keeps the real removal logged and waits for the new ring`() {
        // I1: removed 10 days ago; the new ring goes in at the next minute. The removal stays the logged one.
        val removedAt = LocalDateTime(2026, 10, 24, 9, 0)
        val start = LocalDateTime(2026, 11, 3, 10, 1)
        val plan = ready(SetupStart.plan(SetupStatus.NOT_STARTED, method, start, now, removedAt))
        assertEquals(start, plan.config.anchor)
        assertTrue(plan.config.firstInsertPending)
        val removal = plan.logs.single()
        assertEquals(EventKind.REMOVE, removal.kind)
        assertEquals(removedAt, removal.plannedAt)
        assertEquals(removedAt, removal.confirmedAt)
        // The logged removal is no event of the new schedule, so nothing else counts as confirmed.
        assertEquals(EventKind.INSERT, OverdueCalculator.nextPending(ScheduleEngine.generate(plan.config), plan.logs)!!.kind)
    }

    @Test
    fun `pause over still rejects a start that is not ahead`() {
        val removedAt = LocalDateTime(2026, 10, 24, 9, 0)
        assertEquals(SetupPlan.InPast, SetupStart.plan(SetupStatus.NOT_STARTED, method, LocalDateTime(2026, 11, 3, 10, 0), now, removedAt))
    }
}
