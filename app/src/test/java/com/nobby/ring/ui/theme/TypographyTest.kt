package com.nobby.ring.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TypographyTest {
    private val t = NobbyTypography

    @Test
    fun `display, headline, titleLarge and labelLarge use Fredoka, the rest Nunito Sans`() {
        listOf(t.displayLarge, t.displayMedium, t.headlineLarge, t.headlineMedium, t.headlineSmall, t.titleLarge, t.labelLarge)
            .forEach { assertSame(Fredoka, it.fontFamily) }
        listOf(t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium, t.bodySmall, t.labelMedium, t.labelSmall)
            .forEach { assertSame(NunitoSans, it.fontFamily) }
    }

    @Test
    fun `displayLarge is 57 sp so the Material time picker header digits fit (v1_1 fix 2)`() {
        assertEquals(57.sp, t.displayLarge.fontSize)
        assertEquals(64.sp, t.displayLarge.lineHeight)
    }

    @Test
    fun `sizes and weights follow tokens json type`() {
        assertEquals(28.sp, t.headlineMedium.fontSize)
        assertEquals(FontWeight.SemiBold, t.headlineMedium.fontWeight)
        assertEquals(FontWeight.Medium, t.headlineSmall.fontWeight)
        assertEquals(22.sp, t.titleLarge.fontSize)
        assertEquals(FontWeight.Bold, t.titleMedium.fontWeight)
        assertEquals(16.sp, t.bodyLarge.fontSize)
        assertEquals(FontWeight.Normal, t.bodyLarge.fontWeight)
        assertEquals(FontWeight.Medium, t.labelLarge.fontWeight)
        assertEquals(11.sp, t.labelSmall.fontSize)
    }

    @Test
    fun `countdown is 96 sp Fredoka SemiBold with tabular figures`() {
        val c = NobbyType.countdown
        assertSame(Fredoka, c.fontFamily)
        assertEquals(96.sp, c.fontSize)
        assertEquals(96.sp, c.lineHeight)
        assertEquals(FontWeight.SemiBold, c.fontWeight)
        assertEquals("tnum", c.fontFeatureSettings)
    }
}
