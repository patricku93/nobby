package com.nobby.ring.ui.ring

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingModelTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private fun at(now: LocalDateTime, logs: List<EventLog> = emptyList()) = RingModel.from(ScheduleRepository.buildState(config, logs, now))

    @Test
    fun `wear day 12 of 21 is happy with 11 beads done and 10 days to removal`() {
        val m = at(LocalDateTime(2026, 11, 12, 12, 0))
        assertEquals(RingTone.WEAR, m.tone)
        assertEquals(MascotMood.HAPPY, m.mood)
        assertEquals(21, m.beads)
        assertEquals(11, m.done)
        assertEquals(10, m.number)
        assertEquals(RingLabel.UNTIL_REMOVE, m.label)
    }

    @Test
    fun `due today is neutral and shows the word instead of a number`() {
        val m = at(LocalDateTime(2026, 11, 22, 10, 0))
        assertEquals(RingTone.DUE, m.tone)
        assertEquals(MascotMood.NEUTRAL, m.mood)
        assertEquals(RingLabel.TODAY, m.label)
        assertNull(m.number)
        assertEquals(21, m.done)
    }

    @Test
    fun `overdue two days is worried`() {
        val m = at(LocalDateTime(2026, 11, 24, 22, 0))
        assertEquals(RingTone.OVERDUE, m.tone)
        assertEquals(MascotMood.WORRIED, m.mood)
        assertEquals(RingLabel.OVERDUE_DAYS, m.label)
        assertEquals(2, m.number)
    }

    @Test
    fun `overdue the same evening counts hours`() {
        val m = at(LocalDateTime(2026, 11, 22, 23, 30))
        assertEquals(RingLabel.OVERDUE_HOURS, m.label)
        assertEquals(2, m.number)
    }

    @Test
    fun `beyond the safety threshold is alert`() {
        val m = at(LocalDateTime(2026, 11, 30, 22, 0))
        assertEquals(RingTone.SAFETY, m.tone)
        assertEquals(MascotMood.ALERT, m.mood)
        assertTrue(m.safety)
        assertEquals(8, m.number)
    }

    @Test
    fun `pause day 4 of 7 is sleepy with 4 days to insertion`() {
        val m = at(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed))
        assertEquals(RingTone.PAUSE, m.tone)
        assertEquals(MascotMood.SLEEPY, m.mood)
        assertEquals(7, m.beads)
        assertEquals(3, m.done)
        assertEquals(4, m.number)
        assertEquals(RingLabel.UNTIL_INSERT, m.label)
    }
}
