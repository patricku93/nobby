package com.nobby.ring.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.ui.components.RingTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RingBitmapsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var fredoka: Typeface

    @Before
    fun setUp() {
        fredoka = ResourcesCompat.getFont(context, R.font.fredoka_variable)!!
        RingBitmaps.clearCache()
    }

    private val edge = 220
    private val density = 1f
    private val wear = RingSpec(RingTone.WEAR, beads = 21, done = 5, number = "16", label = "Tage bis raus", edgePx = edge, density = density)

    private fun Bitmap.alphaAt(x: Int, y: Int): Int = getPixel(x, y) ushr 24
    private fun List<RingImage>.of(layer: RingLayer): Bitmap = first { it.layer == layer }.bitmap

    /** Centre of bead [i] of [beads], with the painter's geometry (track 8.5 %, 4 dp inset). */
    private fun bead(i: Int, beads: Int): Pair<Int, Int> {
        val s = edge.toFloat()
        val tw = s * 0.085f
        val r = s / 2f - tw / 2f - 4f * density
        val a = Math.toRadians((-90f + 360f / beads * (i + 0.5f)).toDouble())
        return (s / 2f + r * cos(a)).roundToInt() to (s / 2f + r * sin(a)).roundToInt()
    }

    private fun countOpaque(b: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) if (b.alphaAt(x, y) > 128) n++
        return n
    }

    @Test
    fun `each layer holds its own part of the bracelet`() {
        val layers = RingBitmaps.render(wear, fredoka)
        assertEquals(listOf(RingLayer.TRACK, RingLayer.HALO, RingLayer.BEAD, RingLayer.FUTURE, RingLayer.TEXT), layers.map { it.layer })
        layers.forEach {
            assertEquals(edge, it.bitmap.width)
            assertEquals(edge, it.bitmap.height)
            assertEquals(Bitmap.Config.ALPHA_8, it.bitmap.config)
        }
        val (px, py) = bead(0, 21)
        assertTrue("past bead 0 in BEAD", layers.of(RingLayer.BEAD).alphaAt(px, py) > 200)
        val (cx, cy) = bead(5, 21)
        assertTrue("today's bead core in BEAD", layers.of(RingLayer.BEAD).alphaAt(cx, cy) > 200)
        assertTrue("today's halo in HALO", layers.of(RingLayer.HALO).alphaAt(cx, cy) > 200)
        val (fx, fy) = bead(15, 21)
        assertTrue("future dot in FUTURE", layers.of(RingLayer.FUTURE).alphaAt(fx, fy) > 200)
        assertEquals("no future dot in BEAD", 0, layers.of(RingLayer.BEAD).alphaAt(fx, fy))
        assertTrue("track under the future dot", layers.of(RingLayer.TRACK).alphaAt(fx, fy) > 200)
        assertEquals("track is a ring, not a disc", 0, layers.of(RingLayer.TRACK).alphaAt(edge / 2, edge / 2))
    }

    @Test
    fun `number and label are drawn in the centre and stay inside the beads`() {
        val layers = RingBitmaps.render(wear, fredoka)
        val text = layers.of(RingLayer.TEXT)
        val c = edge / 2
        assertTrue("number pixels near the centre", countOpaque(text, c - 40, c - 50, c + 40, c + 10) > 100)
        assertTrue("label pixels below the number", countOpaque(text, c - 60, c + 5, c + 60, c + 40) > 20)
        val (bx, by) = bead(0, 21)
        assertEquals("no text on the beads", 0, countOpaque(text, bx - 6, by - 6, bx + 6, by + 6))
        assertEquals("no number in the BEAD layer when calm", 0, countOpaque(layers.of(RingLayer.BEAD), c - 20, c - 20, c + 20, c + 20))
    }

    @Test
    fun `an urgent number is drawn with the bead tint and safety adds the disc`() {
        val overdue = wear.copy(tone = RingTone.SAFETY, done = 21, number = "8")
        val layers = RingBitmaps.render(overdue, fredoka)
        assertEquals(RingLayer.SAFETY, layers.first().layer)
        val c = edge / 2
        assertTrue("number in BEAD", countOpaque(layers.of(RingLayer.BEAD), c - 30, c - 50, c + 30, c + 10) > 50)
        assertTrue("safety disc fills the centre", layers.of(RingLayer.SAFETY).alphaAt(c, c + 60) > 100)
    }

    @Test
    fun `more than 35 beads draw the arc`() {
        val layers = RingBitmaps.render(wear.copy(beads = 40, done = 20), fredoka)
        val (x, y) = bead(5, 40)
        assertTrue(layers.of(RingLayer.BEAD).alphaAt(x, y) > 200)
        val (ex, ey) = bead(30, 40)
        assertEquals(0, layers.of(RingLayer.BEAD).alphaAt(ex, ey))
    }

    @Test
    fun `edge is the widget size in pixels, capped so all layers stay under the byte budget`() {
        assertEquals(200, RingBitmaps.edgePx(sizeDp = 100f, density = 2f, layers = 5))
        for (layers in 5..6) for (dp in listOf(50f, 150f, 400f, 1200f)) for (d in listOf(1f, 2.75f, 4f)) {
            val e = RingBitmaps.edgePx(dp, d, layers)
            assertTrue("$dp dp @ $d", e >= 1 && e <= (dp * d).roundToInt())
            assertTrue("$dp dp @ $d x $layers layers = ${e * e * layers} B", e * e * layers <= RingBitmaps.MAX_BYTES)
        }
        val big = RingBitmaps.render(wear.copy(edgePx = RingBitmaps.edgePx(1200f, 4f, 5)), fredoka)
        assertTrue(big.sumOf { it.bitmap.byteCount } <= RingBitmaps.MAX_BYTES)
    }

    @Test
    fun `renders are cached by spec`() {
        val a = RingBitmaps.renderOrNull(wear) { fredoka }
        val b = RingBitmaps.renderOrNull(wear) { fredoka }
        assertNotNull(a)
        assertSame(a, b)
        val other = RingBitmaps.renderOrNull(wear.copy(edgePx = 100)) { fredoka }
        assertTrue(other !== a)
    }

    @Test
    fun `a failing render returns null so the widget falls back to classic`() {
        val failed = RingBitmaps.renderOrNull(wear, font = { fredoka }, render = { _, _ -> error("boom") })
        assertNull(failed)
        assertFalse(useRingLayout(WidgetConfig(), failed))
        val noFont = RingBitmaps.renderOrNull(wear.copy(number = "9")) { error("no font") }
        assertNull(noFont)
    }
}
