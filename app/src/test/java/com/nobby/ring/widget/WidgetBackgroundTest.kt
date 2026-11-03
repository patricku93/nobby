package com.nobby.ring.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetBackgroundTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()

    private fun context(night: Boolean): Context {
        val c = Configuration(app.resources.configuration)
        c.uiMode = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return app.createConfigurationContext(c)
    }

    private val steps = (0..100 step 10).toList()

    @Test
    fun `opacity sets only the alpha of the background in every preset and mode`() {
        for (p in Palette.entries) for (dark in listOf(false, true)) for (op in steps) {
            val w = p.widgetColors(dark)
            val bg = w.background(op)
            assertEquals("$p dark=$dark $op", op / 100f, bg.alpha, 0.002f)
            assertEquals(w.bg.copy(alpha = 1f), bg.copy(alpha = 1f))
        }
    }

    @Test
    fun `the day and night providers carry the opacity for system, light and dark`() {
        for (p in Palette.entries) for (op in steps) {
            val light = p.widgetColors(false)
            val dark = p.widgetColors(true)
            for (mode in ThemeMode.entries) {
                val provider = WidgetPalette(light, dark, mode).background(op)
                for (night in listOf(false, true)) {
                    val expectDark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && night)
                    val expected = (if (expectDark) dark else light).bg.copy(alpha = op / 100f)
                    val actual = provider.getColor(context(night))
                    assertEquals("$p $mode night=$night $op", expected.alpha, actual.alpha, 0.002f)
                    assertEquals("$p $mode night=$night $op", expected.copy(alpha = 1f), actual.copy(alpha = 1f))
                }
            }
        }
    }

    @Test
    fun `text and accent colours stay opaque`() {
        for (p in Palette.entries) for (dark in listOf(false, true)) {
            val w = p.widgetColors(dark)
            listOf(w.fg, w.sub, w.hi, w.onHi, w.err, w.onErr).forEach { assertEquals(1f, it.alpha, 0f) }
        }
    }
}
