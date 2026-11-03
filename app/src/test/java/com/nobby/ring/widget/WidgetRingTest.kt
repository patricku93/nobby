package com.nobby.ring.widget

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.ringColors
import com.nobby.ring.ui.ring.RingModel
import com.nobby.ring.ui.theme.colorScheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRingTest {
    private val strings = WidgetStrings(
        nextAny = "Next", nextRemove = "Remove", nextInsert = "Insert", overdue = "Overdue", cycleDay = "Cycle", none = "All done",
        inDays = { "in $it d" }, overdueDays = { "$it d overdue" }, overdueHours = { "$it h overdue" }, cycleDayFmt = { d, n -> "Day $d of $n" },
        kindLabel = { it.name.lowercase() }, ringLabel = { l, _ -> l.name.lowercase() }, today = "Today",
        ringCd = { n, l -> "$n $l" }, ringCdToday = { t, k -> "$t: $k" }, withDetail = { a, b -> "$a, $b" },
        notStarted = "Not started", dayOf = { a, b -> "Day $a/$b" },
    )
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))

    private fun stateAt(now: LocalDateTime, logs: List<EventLog> = emptyList()) = ScheduleRepository.buildState(config, logs, now)

    private val cases = mapOf(
        RingTone.WEAR to stateAt(LocalDateTime(2026, 11, 12, 12, 0)),
        RingTone.PAUSE to stateAt(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed)),
        RingTone.DUE to stateAt(LocalDateTime(2026, 11, 22, 10, 0)),
        RingTone.OVERDUE to stateAt(LocalDateTime(2026, 11, 24, 22, 0)),
        RingTone.SAFETY to stateAt(LocalDateTime(2026, 11, 30, 22, 0)),
    )

    @Test
    fun `widget ring parameters follow the Home ring model for every tone`() {
        cases.forEach { (tone, state) ->
            val ring = RingModel.from(state)
            val data = WidgetModel.build(state, WidgetConfig(), strings)
            val spec = data.ringSpec(withLabel = true)
            assertEquals(tone, ring.tone)
            assertEquals("$tone", ring.tone, spec.tone)
            assertEquals("$tone", ring.beads, spec.beads)
            assertEquals("$tone", ring.done, spec.done)
            assertEquals("$tone", data.head().number, spec.number)
            assertEquals("$tone", data.head().label, spec.label)
            assertEquals("$tone", tone == RingTone.OVERDUE || tone == RingTone.SAFETY, spec.urgent)
        }
    }

    @Test
    fun `wear day 12 gives 21 beads, 11 done and the number 10`() {
        val spec = WidgetModel.build(cases.getValue(RingTone.WEAR), WidgetConfig(), strings).ringSpec(withLabel = true)
        assertEquals(21, spec.beads)
        assertEquals(11, spec.done)
        assertEquals("10", spec.number)
        assertEquals("until_remove", spec.label)
    }

    @Test
    fun `due shows the word today and the small ring has no label`() {
        val spec = WidgetModel.build(cases.getValue(RingTone.DUE), WidgetConfig(), strings).ringSpec(withLabel = false)
        assertEquals("Today", spec.number)
        assertNull(spec.label)
    }

    @Test
    fun `without a schedule the ring shows a dash and the none label`() {
        val spec = WidgetModel.build(null, WidgetConfig(), strings).ringSpec(withLabel = true)
        assertEquals("–", spec.number)
        assertEquals("All done", spec.label)
    }

    @Test
    fun `widget ring tints are the Home ring colours of every preset, mode and tone`() {
        for (p in Palette.entries) for (dark in listOf(false, true)) for (tone in RingTone.entries) {
            assertEquals("$p $dark $tone", p.colorScheme(dark).ringColors(tone), p.widgetColors(dark).ring(tone))
        }
    }

    @Test
    fun `the ring description carries number, label and the phase day or overdue event`() {
        val d = cases.mapValues { (_, st) -> WidgetModel.build(st, WidgetConfig(), strings).description }
        assertEquals("10 until_remove, Day 12/21", d[RingTone.WEAR])
        assertEquals("Today: remove", d[RingTone.DUE]!!.substringBefore(","))
        assertTrue(d[RingTone.OVERDUE]!!, d[RingTone.OVERDUE]!!.endsWith(", remove"))
        assertTrue(d[RingTone.SAFETY]!!, d[RingTone.SAFETY]!!.endsWith(", remove") && d[RingTone.SAFETY]!!.startsWith("8 "))
        d.forEach { (tone, text) -> assertTrue("$tone: $text", text.isNotBlank() && !text.startsWith("–")) }
        assertEquals("All done", WidgetModel.build(null, WidgetConfig(), strings).description)
    }
}
