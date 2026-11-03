package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigChangeTest {
    private val old = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removal = EventLog(
        eventId(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0)), EventKind.REMOVE,
        LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 23, 0),
    )

    @Test
    fun `a new time of day re-keys the removal and keeps planned and confirmed time`() {
        val new = old.copy(method = Method.ring(21, 7, LocalTime(20, 0)))
        val carried = ConfigChange.carriedRemoval(old, new, listOf(removal))!!
        assertEquals(removal, carried.from)
        assertEquals(removal.copy(eventId = eventId(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 20, 0))), carried.to)
    }

    @Test
    fun `nothing is carried when there is nothing to re-key`() {
        val newTime = old.copy(method = Method.ring(21, 7, LocalTime(20, 0)))
        assertNull(ConfigChange.carriedRemoval(old, newTime, emptyList())) // nothing confirmed
        assertNull(ConfigChange.carriedRemoval(old, old.copy(method = Method.ring(21, 5, LocalTime(21, 0))), listOf(removal))) // same id
        assertNull(ConfigChange.carriedRemoval(old, newTime.copy(method = Method.ring(21, 0, LocalTime(20, 0))), listOf(removal))) // SWAP
        assertNull(ConfigChange.carriedRemoval(old, old.copy(anchor = LocalDateTime(2026, 9, 20, 21, 0)), listOf(removal))) // D9
        assertNull(ConfigChange.carriedRemoval(old, old.copy(anchor = LocalDateTime(2026, 11, 24, 9, 0)), listOf(removal))) // ring back in
    }
}
