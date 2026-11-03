package com.nobby.ring.ui.settings

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDraftTest {
    private val now = LocalDateTime(2026, 11, 3, 17, 25, 37)
    private val pending = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 6, 21, 0), firstInsertPending = true)

    @Test
    fun `Apply keeps a schedule that has not started yet waiting for its first insertion`() {
        val draft = ScheduleDraft.from(pending, now = now).copy(wearDays = 22)
        assertTrue(draft.toConfig().firstInsertPending)
        assertTrue(draft.waitingForFirstRing)
        assertEquals(EventKind.INSERT, draft.preview.first().kind)
    }

    @Test
    fun `a past date while waiting means the ring is in since then`() {
        // I3: the user put the ring in but did not tap Done, and enters the real insertion here.
        val inserted = LocalDateTime(2026, 11, 1, 21, 0)
        val draft = ScheduleDraft.from(pending, now = now).copy(anchor = inserted, anchorEdited = true)
        val config = draft.toConfig()
        assertFalse(config.firstInsertPending)
        assertEquals(inserted, config.anchor)
        assertFalse(draft.waitingForFirstRing)
        assertEquals(EventKind.REMOVE, draft.preview.first().kind)
    }

    @Test
    fun `the current minute counts as past, like the onboarding check`() {
        val draft = ScheduleDraft.from(pending, now = now).copy(anchor = LocalDateTime(2026, 11, 3, 17, 25), anchorEdited = true)
        assertFalse(draft.toConfig().firstInsertPending)
    }

    @Test
    fun `a started schedule never turns into a waiting one`() {
        val started = pending.copy(firstInsertPending = false)
        val draft = ScheduleDraft.from(started, now = now).copy(anchor = LocalDateTime(2026, 11, 20, 21, 0), anchorEdited = true)
        assertFalse(draft.toConfig().firstInsertPending)
        assertFalse(draft.waitingForFirstRing)
    }
    /** Waiting since a start 1 h 25 min ago: the first insertion is overdue and not entered. */
    private val overdueStart = ScheduleConfig(Method.ring(21, 7, LocalTime(16, 0)), LocalDateTime(2026, 11, 3, 16, 0), firstInsertPending = true)

    @Test
    fun `Apply without touching the start keeps a waiting schedule with an overdue first ring waiting (I1)`() {
        listOf<(ScheduleDraft) -> ScheduleDraft>(
            { it.copy(wearDays = 22) },
            { it.copy(pauseDays = 6) },
            { it.copy(horizonMonths = 6) },
        ).forEach { edit ->
            val draft = edit(ScheduleDraft.from(overdueStart, now = now))
            assertTrue(draft.toConfig().firstInsertPending)
            assertTrue(draft.waitingForFirstRing)
            assertEquals(EventKind.INSERT, draft.preview.first().kind)
        }
    }

    @Test
    fun `a routine time before now keeps a waiting schedule waiting (I1)`() {
        // The routine-time picker also moves the start's time: 21:00 today becomes 17:00, before now (17:25).
        val today = pending.copy(anchor = LocalDateTime(2026, 11, 3, 21, 0))
        val t = LocalTime(17, 0)
        val draft = ScheduleDraft.from(today, now = now).let { it.copy(timeOfDay = t, anchor = LocalDateTime(it.anchor.date, t)) }
        assertTrue(draft.toConfig().firstInsertPending)
        assertTrue(draft.waitingForFirstRing)
        assertEquals(EventKind.INSERT, draft.preview.first().kind)
    }
}
