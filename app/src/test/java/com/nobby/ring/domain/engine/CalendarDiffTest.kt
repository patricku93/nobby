package com.nobby.ring.domain.engine

import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDiffTest {
    private val t1 = LocalDateTime(2026, 11, 22, 21, 0)
    private val t2 = LocalDateTime(2026, 11, 29, 21, 0)
    private val e1 = CalendarEntry("REMOVE@$t1", "Remove ring", t1, 30, CalendarEntry.marker("REMOVE@$t1"))
    private val e2 = CalendarEntry("INSERT@$t2", "Insert ring", t2, 30, CalendarEntry.marker("INSERT@$t2"))

    @Test
    fun `everything is created when no links exist`() {
        val r = CalendarDiff.compute(listOf(e1, e2), emptyList())
        assertEquals(listOf(e1, e2), r.create)
        assertTrue(r.update.isEmpty() && r.delete.isEmpty())
    }

    @Test
    fun `unchanged linked entries are left alone`() {
        val links = listOf(CalendarLink(e1.eventId, 10, t1, "Remove ring"))
        val r = CalendarDiff.compute(listOf(e1), links)
        assertTrue(r.create.isEmpty() && r.update.isEmpty() && r.delete.isEmpty())
    }

    @Test
    fun `changed title triggers update`() {
        val links = listOf(CalendarLink(e1.eventId, 10, t1, "Ring entfernen"))
        val r = CalendarDiff.compute(listOf(e1), links)
        assertEquals(1, r.update.size)
        assertEquals(10L, r.update[0].first.providerEventId)
    }

    @Test
    fun `stale links are deleted`() {
        val links = listOf(CalendarLink("REMOVE@2026-10-01T21:00", 7, LocalDateTime(2026, 10, 1, 21, 0), "Remove ring"))
        val r = CalendarDiff.compute(listOf(e1), links)
        assertEquals(listOf(links[0]), r.delete)
        assertEquals(listOf(e1), r.create)
    }
}
