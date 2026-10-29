package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmActionTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val events = ScheduleEngine.generate(config)
    private val remove = events[0]
    private val insert = events[1]

    @Test
    fun `on-time confirmation needs no policy choice`() {
        assertFalse(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 21, 30)))
        assertTrue(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 23, 0)))
    }

    @Test
    fun `remove with KEEP leaves schedule unchanged and logs event`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.KEEP)
        assertEquals(config, r.config)
        assertEquals(1, r.logs.size)
        assertEquals(remove.id, r.logs[0].eventId)
        assertEquals(LocalDateTime(2026, 11, 24, 21, 0), r.logs[0].confirmedAt)
    }

    @Test
    fun `remove with SHIFT moves following insert by the delay`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.SHIFT)
        val newEvents = ScheduleEngine.generate(r.config)
        val nextInsert = OverdueCalculator.nextPending(newEvents, r.logs)!!
        assertEquals(EventKind.INSERT, nextInsert.kind)
        assertEquals(LocalDateTime(2026, 12, 1, 21, 0), nextInsert.plannedAt)
    }

    @Test
    fun `remove with SHIFT keeps the remove confirmed`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.SHIFT)
        val newEvents = ScheduleEngine.generate(r.config)
        assertTrue(newEvents.none { it.kind == EventKind.REMOVE && it.id !in r.logs.map { l -> l.eventId }.toSet() && it.plannedAt < LocalDateTime(2026, 12, 1, 21, 0) })
    }

    @Test
    fun `insert re-anchors at planned time with KEEP`() {
        val r = ConfirmAction.confirm(config, insert, emptyList(), LocalDateTime(2026, 11, 30, 9, 0), ShiftPolicy.KEEP)
        assertEquals(insert.plannedAt, r.config.anchor)
    }

    @Test
    fun `insert re-anchors at actual time with SHIFT`() {
        val at = LocalDateTime(2026, 11, 30, 9, 0)
        val r = ConfirmAction.confirm(config, insert, emptyList(), at, ShiftPolicy.SHIFT)
        assertEquals(at, r.config.anchor)
        val next = ScheduleEngine.generate(r.config)[0]
        assertEquals(LocalDateTime(2026, 12, 21, 21, 0), next.plannedAt)
    }

    @Test
    fun `swap clears skipPauseOnce and re-anchors`() {
        val skipConfig = config.copy(skipPauseOnce = true)
        val swap = ScheduleEngine.generate(skipConfig)[0]
        assertEquals(EventKind.SWAP, swap.kind)
        val r = ConfirmAction.confirm(skipConfig, swap, emptyList(), swap.plannedAt, ShiftPolicy.KEEP)
        assertFalse(r.config.skipPauseOnce)
        assertEquals(swap.plannedAt, r.config.anchor)
    }

    @Test
    fun `remove with SHIFT logs one entry keyed by the shifted id with the original planned time`() {
        val confirmedAt = LocalDateTime(2026, 11, 24, 21, 0)
        val r = ConfirmAction.confirm(config, remove, emptyList(), confirmedAt, ShiftPolicy.SHIFT)
        assertEquals(1, r.logs.size)
        assertEquals("REMOVE@2026-11-24T21:00", r.logs[0].eventId)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), r.logs[0].plannedAt)
        assertEquals(confirmedAt, r.logs[0].confirmedAt)
        val next = OverdueCalculator.nextPending(ScheduleEngine.generate(r.config), r.logs)!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 12, 1, 21, 0), next.plannedAt)
    }

    @Test
    fun `remove with SHIFT and sub-day delay logs a single entry`() {
        val confirmedAt = LocalDateTime(2026, 11, 22, 23, 30)
        val r = ConfirmAction.confirm(config, remove, emptyList(), confirmedAt, ShiftPolicy.SHIFT)
        assertEquals(1, r.logs.size)
        assertEquals("REMOVE@2026-11-22T21:00", r.logs[0].eventId)
        val next = OverdueCalculator.nextPending(ScheduleEngine.generate(r.config), r.logs)!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), next.plannedAt)
    }

    @Test
    fun `remove with SHIFT and early confirmation shifts earlier`() {
        val confirmedAt = LocalDateTime(2026, 11, 21, 21, 0)
        val r = ConfirmAction.confirm(config, remove, emptyList(), confirmedAt, ShiftPolicy.SHIFT)
        assertEquals(1, r.logs.size)
        val next = OverdueCalculator.nextPending(ScheduleEngine.generate(r.config), r.logs)!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 11, 28, 21, 0), next.plannedAt)
    }

    @Test
    fun `needsPolicyChoice boundary`() {
        assertFalse(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 21, 59)))
        assertTrue(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 22, 0)))
        assertTrue(ConfirmAction.needsPolicyChoice(remove, LocalDateTime(2026, 11, 22, 20, 0)))
    }

    @Test
    fun `confirm appends to existing logs`() {
        val existing = EventLog(
            "INSERT@2026-11-01T21:00",
            EventKind.INSERT,
            LocalDateTime(2026, 11, 1, 21, 0),
            LocalDateTime(2026, 11, 1, 21, 0),
        )
        val r = ConfirmAction.confirm(config, remove, listOf(existing), LocalDateTime(2026, 11, 22, 21, 0), ShiftPolicy.KEEP)
        assertEquals(2, r.logs.size)
        assertEquals(existing, r.logs[0])
        assertEquals(remove.id, r.logs[1].eventId)
    }
}
