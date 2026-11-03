package com.nobby.ring.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.WidgetConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack text of the ring and the not-started label with the real DE / EN resources. */
@RunWith(RobolectricTestRunner::class)
class WidgetStringsResTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val pending = config.copy(anchor = LocalDateTime(2026, 11, 6, 21, 0), firstInsertPending = true)

    private fun build(now: LocalDateTime, c: ScheduleConfig = config) =
        WidgetModel.build(ScheduleRepository.buildState(c, emptyList(), now), WidgetConfig(), widgetStrings(context))

    @Test
    @Config(qualifiers = "de")
    fun `German ring descriptions and start label`() {
        assertEquals("10 Tage bis Ring raus, Tag 12/21", build(LocalDateTime(2026, 11, 12, 12, 0)).description)
        assertEquals("Heute: Ring entfernen, Tag 21/21", build(LocalDateTime(2026, 11, 22, 10, 0)).description)
        assertEquals("2 Tage überfällig, Ring entfernen", build(LocalDateTime(2026, 11, 24, 22, 0)).description)
        assertEquals("Alles erledigt", WidgetModel.build(null, WidgetConfig(), widgetStrings(context)).description)
        val w = build(LocalDateTime(2026, 11, 3, 10, 0), pending)
        assertEquals("Start in 3 Tagen", w.dayLabel)
        assertEquals("3 Tage bis Ring rein, Noch nicht gestartet", w.description)
        assertEquals("Start in 1 Tag", build(LocalDateTime(2026, 11, 5, 10, 0), pending).dayLabel)
        assertEquals("Start heute", build(LocalDateTime(2026, 11, 6, 9, 0), pending).dayLabel)
    }

    @Test
    @Config(qualifiers = "en")
    fun `English ring descriptions and start label`() {
        assertEquals("10 days until removal, Day 12/21", build(LocalDateTime(2026, 11, 12, 12, 0)).description)
        assertEquals("Today: Remove ring, Day 21/21", build(LocalDateTime(2026, 11, 22, 10, 0)).description)
        assertEquals("All done", WidgetModel.build(null, WidgetConfig(), widgetStrings(context)).description)
        assertEquals("Starts in 3 days", build(LocalDateTime(2026, 11, 3, 10, 0), pending).dayLabel)
        assertEquals("Starts in 1 day", build(LocalDateTime(2026, 11, 5, 10, 0), pending).dayLabel)
        assertEquals("Starts today", build(LocalDateTime(2026, 11, 6, 9, 0), pending).dayLabel)
    }
}
