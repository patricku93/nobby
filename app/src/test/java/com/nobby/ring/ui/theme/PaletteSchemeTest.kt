package com.nobby.ring.ui.theme

import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteSchemeTest {
    private val modes = listOf(false, true)

    @Test
    fun `every preset defines all 41 opaque roles in light and dark`() {
        assertEquals(41, PRESET_ROLE_NAMES.size)
        for (p in Palette.entries) for (dark in modes) {
            val roles = p.roles(dark).byName()
            assertEquals("${p.presetId} dark=$dark", PRESET_ROLE_NAMES.toSet(), roles.keys)
            roles.forEach { (name, c) -> assertTrue("${p.presetId}.$name opaque", c.alpha == 1f) }
        }
    }

    @Test
    fun `colour scheme copies the table without derived colours`() {
        val r = Palette.PEACH.roles(true)
        val s = Palette.PEACH.colorScheme(true)
        assertEquals(r.primary, s.primary)
        assertEquals(r.secondaryContainer, s.secondaryContainer)
        assertEquals(r.surfaceContainerHighest, s.surfaceContainerHighest)
        assertEquals(r.surfaceDim, s.surfaceDim)
        assertEquals(r.inverseSurface, s.inverseSurface)
        assertEquals(r.outlineVariant, s.outlineVariant)
        assertEquals(Color.Transparent, s.surfaceTint)
    }

    @Test
    fun `berry is the default and matches the bundle hero colour`() {
        assertEquals(Palette.BERRY, com.nobby.ring.domain.model.ThemeSpec().palette)
        assertEquals(Color(0xFFB03A62), Palette.BERRY.roles(false).primary)
        assertEquals(Color(0xFFF5A6BE), Palette.BERRY.roles(true).primary)
    }

    @Test
    fun `matcha pause is soft blue and peach overdue is the redder tomato`() {
        assertEquals(Color(0xFF386D87), Palette.MATCHA.roles(false).secondary)
        assertEquals(Color(0xFF99CDEC), Palette.MATCHA.roles(true).secondary)
        assertEquals(Color(0xFFB92536), Palette.PEACH.roles(false).error)
        assertEquals(Color(0xFFFFB3AF), Palette.PEACH.roles(true).error)
    }

    @Test
    fun `extra colours come from the same preset table`() {
        val r = Palette.LILAC.roles(false)
        val x = Palette.LILAC.extraColors(false)
        assertFalse(x.dark)
        assertEquals(r.primaryContainer, x.wearBand)
        assertEquals(r.secondaryContainer, x.pauseBand)
        assertEquals(r.accent, x.sticker)
        assertEquals(r.mascot, x.mascotBody)
        assertEquals(r.mascotCheek, x.mascotCheek)
        assertEquals(r.stickerRim, x.mascotRim)
        assertEquals(r.ink, x.ink)
        assertEquals(r.error, x.overdue)
        assertEquals(r.errorContainer, x.overdueContainer)
        assertTrue(Palette.LILAC.extraColors(true).dark)
    }
}
