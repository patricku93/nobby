package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigChangeTest {
    private val old = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removal = EventLog(
        eventId(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0)), EventKind.REMOVE,
        LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 23, 0),
    )

    private fun carry(new: ScheduleConfig) = ConfigChange.carriedRemoval(old, new, listOf(removal))

    /** What Apply saves: the carried config, or [new] unchanged when nothing is carried. */
    private fun saved(new: ScheduleConfig) = carry(new)?.config ?: new

    private fun cycle0Insert(config: ScheduleConfig) =
        ScheduleEngine.generate(config).first { it.cycleIndex == 0 && it.kind == EventKind.INSERT }.plannedAt

    private fun minutesBetween(from: LocalDateTime, to: LocalDateTime) =
        (to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)).inWholeMinutes

    @Test
    fun `a new time of day re-keys the removal and keeps planned and confirmed time`() {
        val new = old.copy(method = Method.ring(21, 7, LocalTime(20, 0)))
        val carried = carry(new)!!
        assertEquals(removal, carried.from)
        assertEquals(removal.copy(eventId = eventId(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 20, 0))), carried.to)
        assertEquals(new, carried.config)
    }

    @Test
    fun `nothing is carried when nothing is confirmed`() {
        assertNull(ConfigChange.carriedRemoval(old, old.copy(method = Method.ring(21, 7, LocalTime(20, 0))), emptyList()))
    }

    @Test
    fun `nothing is carried when the remove id is unchanged`() {
        assertNull(carry(old.copy(method = Method.ring(21, 5, LocalTime(21, 0)))))
    }

    @Test
    fun `nothing is carried when the new schedule swaps instead of removing`() {
        assertNull(carry(old.copy(method = Method.ring(21, 0, LocalTime(20, 0)))))
    }

    @Test
    fun `nothing is carried to an earlier disjoint cycle (D9)`() {
        assertNull(carry(old.copy(anchor = LocalDateTime(2026, 9, 20, 21, 0))))
    }

    @Test
    fun `nothing is carried when the new start is after the removal (ring back in)`() {
        assertNull(carry(old.copy(anchor = LocalDateTime(2026, 11, 24, 9, 0))))
    }

    @Test
    fun `a new ring put in on the removal's own day is not the same ring (I-B)`() {
        assertNull(carry(old.copy(anchor = LocalDateTime(2026, 11, 22, 21, 0)))) // before the 23:00 confirmation
        assertNull(carry(old.copy(anchor = LocalDateTime(2026, 11, 22, 23, 0)))) // the same minute
    }

    @Test
    fun `a carried removal never plans a break longer than the configured pause (I-A)`() {
        val edits = listOf(
            old.copy(method = Method.ring(22, 7, LocalTime(21, 0))), // one wear day more
            old.copy(method = Method.ring(23, 7, LocalTime(21, 0))),
            old.copy(anchor = LocalDateTime(2026, 11, 3, 21, 0)), // start moved forward 2 days
            old.copy(anchor = LocalDateTime(2026, 11, 20, 21, 0)), // start just before the removal
            old.copy(method = Method.ring(22, 8, LocalTime(21, 0))), // wear and pause +1
            old.copy(method = Method.ring(21, 7, LocalTime(20, 0))), // time of day
            old.copy(method = Method.ring(20, 7, LocalTime(21, 0))), // one wear day less
            old.copy(anchor = LocalDateTime(2026, 10, 31, 21, 0)), // start back a day
            old.copy(anchor = LocalDateTime(2026, 10, 12, 21, 0)), // start back 20 days, still overlapping
        )
        edits.forEach { new ->
            val carried = carry(new)
            val config = carried?.config ?: new
            val breakMinutes = minutesBetween(removal.confirmedAt, cycle0Insert(config))
            val label = "${new.method} from ${new.anchor}: break $breakMinutes min"
            assertTrue(label, breakMinutes <= config.method.pauseDays * 1440L)
            if (config.method.pauseDays <= ScheduleConfig.SAFE_MAX_PAUSE) assertTrue(label, breakMinutes <= ScheduleConfig.SAFE_MAX_PAUSE * 1440L)
            assertTrue("$label: INSERT after the removal", breakMinutes > 0)
            assertEquals("$label: removal keyed for the saved config", CycleCalculator.currentRemoveId(config), carried?.to?.eventId ?: removal.eventId)
        }
    }

    @Test
    fun `wear plus one and a start moved forward plan the INSERT at the latest time within the pause (I-A)`() {
        val latest = LocalDateTime(2026, 11, 29, 21, 0) // the latest planned time <= Nov 29 23:00
        assertEquals(latest, cycle0Insert(saved(old.copy(method = Method.ring(22, 7, LocalTime(21, 0))))))
        assertEquals(latest, cycle0Insert(saved(old.copy(anchor = LocalDateTime(2026, 11, 3, 21, 0)))))
        assertEquals(latest, cycle0Insert(saved(old.copy(anchor = LocalDateTime(2026, 11, 20, 21, 0)))))
    }
}
