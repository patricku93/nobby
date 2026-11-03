package com.nobby.ring.ui.calendar

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayInfoTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 10, 12, 0))

    @Test
    fun `november is wear until the 22nd then pause`() {
        val days = DayInfoBuilder.build(state, 2026, 11, setOf(LocalDate(2026, 11, 8)))
        assertEquals(30, days.size)
        assertEquals(DayPhase.WEAR, days[0].phase)
        assertEquals(DayPhase.WEAR, days[20].phase)      // Nov 21
        assertEquals(DayPhase.PAUSE, days[21].phase)     // Nov 22 (remove day counts as pause)
        assertEquals(EventKind.REMOVE, days[21].event)
        assertEquals(EventKind.INSERT, days[28].event)   // Nov 29
        assertEquals(DayPhase.WEAR, days[28].phase)
        assertTrue(days[7].hasNote)
        assertTrue(days[9].isToday)
    }

    @Test
    fun `days before the anchor have no phase`() {
        val days = DayInfoBuilder.build(state, 2026, 10, emptySet())
        assertTrue(days.all { it.phase == DayPhase.NONE })
    }

    @Test
    fun `day in phase counts wear and pause separately`() {
        val days = DayInfoBuilder.build(state, 2026, 11, emptySet())
        assertEquals(3, days[2].dayInPhase)    // Nov 3: wear day 3
        assertEquals(21, days[2].phaseLength)
        assertEquals(1, days[21].dayInPhase)   // Nov 22: pause day 1
        assertEquals(7, days[21].phaseLength)
        assertEquals(1, days[28].dayInPhase)   // Nov 29: new ring, wear day 1
    }
}
