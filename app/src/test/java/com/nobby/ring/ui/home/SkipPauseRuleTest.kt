package com.nobby.ring.ui.home

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skip-pause is offered while the next (or overdue) event is a REMOVE or SWAP, never while an INSERT is next. */
class SkipPauseRuleTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private fun state(now: LocalDateTime, logs: List<EventLog> = emptyList(), skip: Boolean = false) =
        ScheduleRepository.buildState(config.copy(skipPauseOnce = skip), logs, now)

    @Test
    fun `wear with a REMOVE next offers it`() {
        val s = state(LocalDateTime(2026, 11, 12, 12, 0))
        assertEquals(EventKind.REMOVE, s.nextEvent?.kind)
        assertTrue(SkipPauseRule.isOffered(s))
    }

    @Test
    fun `skip active with a SWAP next still offers it so it can be cancelled`() {
        val s = state(LocalDateTime(2026, 11, 12, 12, 0), skip = true)
        assertEquals(EventKind.SWAP, s.nextEvent?.kind)
        assertTrue(SkipPauseRule.isOffered(s))
    }

    @Test
    fun `pause with an INSERT next hides it`() {
        val s = state(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed))
        assertNull(s.overdue)
        assertEquals(EventKind.INSERT, s.nextEvent?.kind)
        assertFalse(SkipPauseRule.isOffered(s))
    }

    @Test
    fun `overdue REMOVE offers it`() {
        val s = state(LocalDateTime(2026, 11, 24, 22, 0))
        assertEquals(EventKind.REMOVE, s.overdue?.event?.kind)
        assertTrue(SkipPauseRule.isOffered(s))
    }

    @Test
    fun `overdue SWAP offers it so the break can be taken after all`() {
        val s = state(LocalDateTime(2026, 11, 24, 22, 0), skip = true)
        assertEquals(EventKind.SWAP, s.overdue?.event?.kind)
        assertTrue(SkipPauseRule.isOffered(s))
    }

    @Test
    fun `overdue INSERT hides it`() {
        val s = state(LocalDateTime(2026, 12, 1, 22, 0), listOf(removed))
        assertEquals(EventKind.INSERT, s.overdue?.event?.kind)
        assertFalse(SkipPauseRule.isOffered(s))
    }
}
