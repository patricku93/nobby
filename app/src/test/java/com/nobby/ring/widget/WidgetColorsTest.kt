package com.nobby.ring.widget

import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.theme.roles
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetColorsTest {
    @Test
    fun `berry matches the bundle widget table`() {
        val l = Palette.BERRY.widgetColors(false)
        val d = Palette.BERRY.widgetColors(true)
        assertEquals(Color(0xFFFFFBF7), l.bg)
        assertEquals(Color(0xFFF2E6DC), l.track)
        assertEquals(Color(0xFFB03A62), l.hi)
        assertEquals(Color(0xFF292022), d.bg)
        assertEquals(Color(0xFF3E3336), d.track)
        assertEquals(Color(0xFFF5A6BE), d.hi)
        assertEquals(Color(0xFF5A1431), d.onHi)
    }

    @Test
    fun `every preset maps from its own table`() {
        for (p in Palette.entries) for (dark in listOf(false, true)) {
            val r = p.roles(dark)
            val w = p.widgetColors(dark)
            assertEquals(r.primary, w.hi)
            assertEquals(r.secondary, w.pause)
            assertEquals(r.error, w.err)
            assertEquals(r.mascot, w.mascotBody)
            assertEquals(r.ink, w.ink)
        }
    }
}
