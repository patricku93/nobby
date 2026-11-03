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
        val draft = ScheduleDraft.from(pending, now = now).copy(anchor = inserted)
        val config = draft.toConfig()
        assertFalse(config.firstInsertPending)
        assertEquals(inserted, config.anchor)
        assertFalse(draft.waitingForFirstRing)
        assertEquals(EventKind.REMOVE, draft.preview.first().kind)
    }

    @Test
    fun `the current minute counts as past, like the onboarding check`() {
        val draft = ScheduleDraft.from(pending, now = now).copy(anchor = LocalDateTime(2026, 11, 3, 17, 25))
        assertFalse(draft.toConfig().firstInsertPending)
    }

    @Test
    fun `a started schedule never turns into a waiting one`() {
        val started = pending.copy(firstInsertPending = false)
        val draft = ScheduleDraft.from(started, now = now).copy(anchor = LocalDateTime(2026, 11, 20, 21, 0))
        assertFalse(draft.toConfig().firstInsertPending)
        assertFalse(draft.waitingForFirstRing)
    }
}
