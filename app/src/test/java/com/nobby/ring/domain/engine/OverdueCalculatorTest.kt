package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class OverdueCalculatorTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val events = ScheduleEngine.generate(config)

    @Test
    fun `nothing overdue before first event`() {
        assertNull(OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 20, 12, 0)))
    }

    @Test
    fun `remove overdue by two hours`() {
        val info = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 22, 23, 0))
        assertNotNull(info)
        assertEquals(EventKind.REMOVE, info!!.event.kind)
        assertEquals(2.hours, info.delay)
        assertFalse(info.safetyBreached)
    }

    @Test
    fun `remove overdue beyond 28 days of wear breaches safety`() {
        val info = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 29, 23, 0))
        assertTrue(info!!.safetyBreached)
    }

    @Test
    fun `insert overdue beyond 7 days of pause breaches safety`() {
        // ring was removed 12 h late (KEEP policy), so the actual pause at 2026-11-29 23:00 is 6 d 14 h
        val removeLog = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 23, 9, 0))
        val ok = OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 29, 23, 0))
        assertEquals(EventKind.INSERT, ok!!.event.kind)
        assertFalse(ok.safetyBreached)
        val bad = OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 30, 22, 0))
        assertTrue(bad!!.safetyBreached)
    }

    @Test
    fun `confirmed event is not overdue`() {
        val log = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 23, 9, 0))
        assertNull(OverdueCalculator.overdue(config, events, listOf(log), LocalDateTime(2026, 11, 23, 12, 0)))
    }

    @Test
    fun `nextPending skips confirmed events`() {
        val log = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
        assertEquals(EventKind.INSERT, OverdueCalculator.nextPending(events, listOf(log))!!.kind)
    }

    @Test
    fun `wear 28 days plus 59 minutes is not breached, plus 61 minutes is`() {
        val atLimit = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 29, 21, 59))
        assertEquals(EventKind.REMOVE, atLimit!!.event.kind)
        assertFalse(atLimit.safetyBreached)
        val over = OverdueCalculator.overdue(config, events, emptyList(), LocalDateTime(2026, 11, 29, 22, 1))
        assertEquals(EventKind.REMOVE, over!!.event.kind)
        assertTrue(over.safetyBreached)
    }

    @Test
    fun `pause 7 days plus 59 minutes is not breached, plus 61 minutes is`() {
        val removeLog = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
        // INSERT is planned at 2026-11-29 21:00, so it is not overdue yet at exactly 7 days
        assertNull(OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 29, 21, 0)))
        val grace = OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 29, 21, 59))
        assertEquals(EventKind.INSERT, grace!!.event.kind)
        assertFalse(grace.safetyBreached)
        val over = OverdueCalculator.overdue(config, events, listOf(removeLog), LocalDateTime(2026, 11, 29, 22, 1))
        assertEquals(EventKind.INSERT, over!!.event.kind)
        assertTrue(over.safetyBreached)

        val lateRemove = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 23, 9, 0))
        val ok = OverdueCalculator.overdue(config, events, listOf(lateRemove), LocalDateTime(2026, 11, 29, 22, 1))
        assertEquals(EventKind.INSERT, ok!!.event.kind)
        assertFalse(ok.safetyBreached)
    }

    @Test
    fun `overdue insert with pause of 7 days plus 59 minutes is not breached`() {
        val lateRemove = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 23, 9, 0))
        // INSERT planned 2026-11-29 21:00 is overdue at 2026-11-30 09:00; pause runs from 2026-11-23 09:00, exactly 7 d
        val atLimit = OverdueCalculator.overdue(config, events, listOf(lateRemove), LocalDateTime(2026, 11, 30, 9, 59))
        assertEquals(EventKind.INSERT, atLimit!!.event.kind)
        assertFalse(atLimit.safetyBreached)
        val over = OverdueCalculator.overdue(config, events, listOf(lateRemove), LocalDateTime(2026, 11, 30, 10, 1))
        assertEquals(EventKind.INSERT, over!!.event.kind)
        assertTrue(over.safetyBreached)
    }

    @Test
    fun `swap overdue uses the wear threshold`() {
        val swapConfig = ScheduleConfig(Method.ring(28, 0, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
        val swapEvents = ScheduleEngine.generate(swapConfig)
        assertEquals(EventKind.SWAP, swapEvents.first().kind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), swapEvents.first().plannedAt)
        // planned at exactly 28 d of wear, so an overdue SWAP passes the threshold once the 60-minute grace is over
        assertNull(OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 29, 21, 0)))
        assertFalse(OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 29, 21, 59))!!.safetyBreached)
        val late = OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 29, 22, 1))
        assertEquals(EventKind.SWAP, late!!.event.kind)
        assertTrue(late.safetyBreached)
        val later = OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 30, 22, 0))
        assertTrue(later!!.safetyBreached)
    }

    @Test
    fun `swap overdue within wear threshold is not breached`() {
        val swapConfig = ScheduleConfig(Method.ring(21, 0, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
        val swapEvents = ScheduleEngine.generate(swapConfig)
        assertEquals(EventKind.SWAP, swapEvents.first().kind)
        val ok = OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 22, 22, 0))
        assertEquals(EventKind.SWAP, ok!!.event.kind)
        assertFalse(ok.safetyBreached)
        assertFalse(OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 29, 21, 59))!!.safetyBreached)
        assertTrue(OverdueCalculator.overdue(swapConfig, swapEvents, emptyList(), LocalDateTime(2026, 11, 29, 22, 1))!!.safetyBreached)
    }

    @Test
    fun `pause safety is measured from the removal logged under this cycle's REMOVE id (I-A)`() {
        // A removal carried by Settings > Apply keeps its original plannedAt, which can be before the new cycle start.
        val remove = events.first { it.kind == EventKind.REMOVE && it.cycleIndex == 0 }
        val carried = EventLog(remove.id, EventKind.REMOVE, LocalDateTime(2026, 10, 30, 21, 0), LocalDateTime(2026, 11, 21, 21, 0))
        val info = OverdueCalculator.overdue(config, events, listOf(carried), LocalDateTime(2026, 11, 29, 21, 30))!!
        assertEquals(EventKind.INSERT, info.event.kind)
        assertTrue("8 days since the real removal", info.safetyBreached)
    }
}
