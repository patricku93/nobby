package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleCalculatorTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))

    @Test
    fun `day 1 of wear right after anchor`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 1, 22, 0))
        assertEquals(PhaseKind.WEAR, p.phase)
        assertEquals(1, p.dayInCycle)
        assertEquals(1, p.dayInPhase)
        assertEquals(28, p.cycleLengthDays)
        assertEquals(21, p.phaseLengthDays)
        assertTrue(p.ringIn)
    }

    @Test
    fun `day 12 of wear`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 12, 23, 0))
        assertEquals(12, p.dayInCycle)
        assertEquals(PhaseKind.WEAR, p.phase)
    }

    @Test
    fun `pause day 3 when remove was confirmed`() {
        val removeLog = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 23, 9, 0))
        val p = CycleCalculator.positionAt(config, listOf(removeLog), LocalDateTime(2026, 11, 24, 23, 0))
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertEquals(24, p.dayInCycle)
        assertEquals(3, p.dayInPhase)
        assertEquals(7, p.phaseLengthDays)
        assertFalse(p.ringIn)
    }

    @Test
    fun `ring still counted as in while remove is unconfirmed`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 24, 23, 0))
        assertTrue(p.ringIn)
        assertEquals(PhaseKind.WEAR, p.phase)
    }

    @Test
    fun `now before anchor yields day 1`() {
        val future = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 10, 21, 0))
        val p = CycleCalculator.positionAt(future, emptyList(), LocalDateTime(2026, 11, 5, 12, 0))
        assertEquals(1, p.dayInCycle)
        assertEquals(1, p.dayInPhase)
        assertEquals(PhaseKind.WEAR, p.phase)
        assertTrue(p.ringIn)
    }

    @Test
    fun `overdue remove reports day beyond phase length`() {
        val p = CycleCalculator.positionAt(config, emptyList(), LocalDateTime(2026, 11, 24, 23, 0))
        assertEquals(24, p.dayInPhase)
        assertEquals(21, p.phaseLengthDays)
        assertEquals(PhaseKind.WEAR, p.phase)
    }

    private fun confirmRemove(confirmedAt: LocalDateTime, policy: ShiftPolicy): ConfirmResult {
        val remove = ScheduleEngine.generate(config).first { it.kind == EventKind.REMOVE }
        return ConfirmAction.confirm(config, remove, emptyList(), confirmedAt, policy)
    }

    @Test
    fun `removal more than wearDays late with SHIFT reports the pause (v1_1 fix 3)`() {
        val result = confirmRemove(LocalDateTime(2026, 12, 15, 10, 0), ShiftPolicy.SHIFT) // planned Nov 22, 23 days late
        assertEquals(LocalDateTime(2026, 11, 23, 21, 0), result.config.anchor)
        val p = CycleCalculator.positionAt(result.config, result.logs, LocalDateTime(2026, 12, 15, 12, 0))
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertEquals(2, p.dayInPhase)
        assertEquals(7, p.phaseLengthDays)
        assertFalse(p.ringIn)
        val next = OverdueCalculator.nextPending(ScheduleEngine.generate(result.config), result.logs)!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 12, 21, 21, 0), next.plannedAt)
    }

    @Test
    fun `removal more than wearDays late with KEEP also reports the pause`() {
        val result = confirmRemove(LocalDateTime(2026, 12, 15, 10, 0), ShiftPolicy.KEEP)
        val p = CycleCalculator.positionAt(result.config, result.logs, LocalDateTime(2026, 12, 15, 12, 0))
        assertEquals(PhaseKind.PAUSE, p.phase)
        assertFalse(p.ringIn)
    }

    @Test
    fun `a removal logged in the previous cycle does not count for the current one`() {
        val next = ScheduleConfig(config.method, LocalDateTime(2026, 11, 29, 21, 0)) // cycle 2 started Nov 29
        val oldRemove = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
        val p = CycleCalculator.positionAt(next, listOf(oldRemove), LocalDateTime(2026, 12, 3, 12, 0))
        assertEquals(PhaseKind.WEAR, p.phase)
        assertEquals(5, p.dayInPhase)
    }
}
