package com.nobby.ring.ui.mascot

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.nobby.ring.domain.model.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MascotPainterTest {
    private val colors = Palette.BERRY.mascotColors(dark = false)

    private fun draw(block: DrawScope.() -> Unit) {
        val image = ImageBitmap(272, 272)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(272f, 272f), block)
    }

    @Test
    fun `every mood with and without every prop draws`() {
        val props = listOf<MascotProp?>(null) + MascotProp.entries
        for (mood in MascotMood.entries) for (prop in props) for (flat in listOf(false, true)) {
            draw { drawNobby(mood, prop, flat, renderDp = 120f, c = colors, paths = MascotPaths()) }
        }
    }

    @Test
    fun `flat faces and the done burst draw`() {
        MascotMood.entries.forEach { mood -> draw { drawNobbyFace(mood, colors, MascotPaths()) } }
        draw { drawBurst(colors, MascotPaths()) }
    }

    @Test
    fun `face bitmap has the requested size`() {
        val bmp = MascotBitmaps.face(MascotMood.WORRIED, Palette.MATCHA.mascotColors(dark = false), 96)
        assertEquals(96, bmp.width)
        assertEquals(96, bmp.height)
        assertTrue("face draws opaque pixels", bmp.getPixel(48, 60) ushr 24 != 0)
    }
}
