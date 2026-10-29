package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {
    private val nine = LocalTime(21, 0)
    private val anchor = LocalDateTime(2026, 11, 1, 21, 0)

    @Test
    fun `standard 21-7 cycle produces remove then insert per cycle`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 2)
        val events = ScheduleEngine.generate(config)

        assertEquals(EventKind.REMOVE, events[0].kind)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), events[0].plannedAt)
        assertEquals(EventKind.INSERT, events[1].kind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), events[1].plannedAt)
        assertEquals(0, events[0].cycleIndex)
        assertEquals(1, events[2].cycleIndex)
        assertEquals(LocalDateTime(2026, 12, 20, 21, 0), events[2].plannedAt)
    }

    @Test
    fun `continuous use with zero pause produces only swaps`() {
        val config = ScheduleConfig(Method.ring(28, 0, nine), anchor, horizonMonths = 3)
        val events = ScheduleEngine.generate(config)

        assertTrue(events.all { it.kind == EventKind.SWAP })
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), events[0].plannedAt)
        assertEquals(LocalDateTime(2026, 12, 27, 21, 0), events[1].plannedAt)
    }

    @Test
    fun `skipPauseOnce turns only the first remove into a swap`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 2, skipPauseOnce = true)
        val events = ScheduleEngine.generate(config)

        assertEquals(EventKind.SWAP, events[0].kind)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), events[0].plannedAt)
        // cycle 1 starts at the swap, no insert in cycle 0
        assertEquals(EventKind.REMOVE, events[1].kind)
        assertEquals(LocalDateTime(2026, 12, 13, 21, 0), events[1].plannedAt)
        assertEquals(1, events[1].cycleIndex)
    }

    @Test
    fun `events stop at the horizon and are sorted`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 12)
        val events = ScheduleEngine.generate(config)
        val last = events.last().plannedAt
        assertTrue(last < LocalDateTime(2027, 11, 1, 21, 0))
        assertEquals(events, events.sortedBy { it.plannedAt })
    }

    @Test
    fun `event ids are deterministic`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 1)
        val events = ScheduleEngine.generate(config)
        assertEquals("REMOVE@2026-11-22T21:00", events[0].id)
    }

    @Test
    fun `time of day applies to every event`() {
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(8, 30)), LocalDateTime(2026, 11, 1, 8, 30), 1)
        val events = ScheduleEngine.generate(config)
        assertTrue(events.all { it.plannedAt.hour == 8 && it.plannedAt.minute == 30 })
    }

    @Test
    fun `invalid config is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleConfig(Method.ring(0, 0, nine), anchor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleConfig(Method.ring(21, 15, nine), anchor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 0)
        }
    }

    @Test
    fun `insert past the horizon is dropped but remove inside is kept`() {
        // Horizon end 2026-12-01T21:00: second cycle's REMOVE (12-20) is outside.
        val first = ScheduleEngine.generate(
            ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 1),
        )
        assertEquals(2, first.size)
        assertEquals(EventKind.REMOVE, first[0].kind)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), first[0].plannedAt)
        assertEquals(EventKind.INSERT, first[1].kind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), first[1].plannedAt)

        // Horizon end 2026-12-05T21:00: REMOVE 11-26 and INSERT 12-03 both inside.
        val second = ScheduleEngine.generate(
            ScheduleConfig(Method.ring(21, 7, nine), LocalDateTime(2026, 11, 5, 21, 0), horizonMonths = 1),
        )
        assertEquals(2, second.size)
        assertEquals(LocalDateTime(2026, 11, 26, 21, 0), second[0].plannedAt)
        assertEquals(EventKind.INSERT, second.last().kind)
        assertEquals(LocalDateTime(2026, 12, 3, 21, 0), second.last().plannedAt)

        // Horizon end 2026-03-01T21:00 (28-day month): REMOVE 02-22 kept, INSERT 03-01 dropped.
        val third = ScheduleEngine.generate(
            ScheduleConfig(Method.ring(21, 7, nine), LocalDateTime(2026, 2, 1, 21, 0), horizonMonths = 1),
        )
        assertEquals(1, third.size)
        assertEquals(EventKind.REMOVE, third.last().kind)
        assertEquals(LocalDateTime(2026, 2, 22, 21, 0), third.last().plannedAt)
    }

    @Test
    fun `skipPauseOnce is consumed after one swap`() {
        val config = ScheduleConfig(Method.ring(21, 7, nine), anchor, horizonMonths = 3, skipPauseOnce = true)
        val events = ScheduleEngine.generate(config)

        assertEquals(EventKind.SWAP, events[0].kind)
        assertEquals(EventKind.REMOVE, events[1].kind)
        assertEquals(EventKind.INSERT, events[2].kind)
        assertEquals(1, events.count { it.kind == EventKind.SWAP })
    }

    @Test
    fun `event times follow method timeOfDay not anchor time`() {
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(8, 30)), anchor, horizonMonths = 3)
        val events = ScheduleEngine.generate(config)

        assertTrue(events.all { it.plannedAt.hour == 8 && it.plannedAt.minute == 30 })
        assertEquals(LocalDateTime(2026, 11, 22, 8, 30), events[0].plannedAt)
    }
}
