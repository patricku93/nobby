package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobby.ring.ui.theme.NobbyType
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
class BeadRingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `bead kinds follow nobby_js ring`() {
        val k = beadKinds(21, 11)
        assertEquals(List(11) { BeadKind.PAST }, k.take(11))
        assertEquals(BeadKind.CURRENT, k[11])
        assertEquals(List(9) { BeadKind.FUTURE }, k.drop(12))
        val all = beadKinds(21, 21)
        assertEquals(List(20) { BeadKind.PAST }, all.take(20))
        assertEquals(BeadKind.CURRENT, all.last())
    }

    @Test
    fun `ring exposes its tone and renders centre content`() {
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                BeadRing(RingTone.PAUSE, beads = 7, done = 3, modifier = Modifier.size(200.dp)) { Text("4") }
            }
        }
        compose.onNodeWithText("4").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(RingToneKey, RingTone.PAUSE)).assertExists()
    }

    @Test
    fun `centre rows scale so every row's corners stay inside the inner disc`() {
        assertEquals(1f, circleFitScale(listOf(100f), listOf(80f), 194f), 0.0001f) // fits as is
        // one 200 x 150 row: corner at hypot(100, 75) = 125 > 97
        assertEquals(97f / 125f, circleFitScale(listOf(200f), listOf(150f), 194f), 0.0001f)
        // narrow tall number on top, wide short line below: the wide line is checked at its own height only
        val rows = circleFitScale(listOf(60f, 180f), listOf(100f, 20f), 194f) // total 120: line 2 spans y 40..60
        assertEquals(97f / kotlin.math.hypot(90f, 60f), rows, 0.0001f)
        // a wide line in the middle fits where one bounding box (180 x 140) would not
        assertEquals(1f, circleFitScale(listOf(60f, 180f, 60f), listOf(60f, 20f, 60f), 194f), 0.0001f)
        assertTrue(circleFitScale(listOf(180f), listOf(140f), 194f) < 1f)
        assertEquals(1f, circleFitScale(emptyList(), emptyList(), 194f), 0.0001f)
        assertEquals(193.84f, ringInnerDiameter(264.dp).value, 0.01f) // 0.81 x 264 - 20
        assertEquals(0f, ringInnerDiameter(10.dp).value, 0f)
    }

    @Test
    fun `wide one-line centre text is scaled inside the disc instead of clipped`() {
        val label = "Tage bis Ring rein Tage bis Ring rein"
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                BeadRing(RingTone.SAFETY, beads = 21, done = 21, modifier = Modifier.size(264.dp)) {
                    CircleFitColumn {
                        Text("22", style = NobbyType.countdown.copy(fontSize = 96.sp, lineHeight = 96.sp))
                        Text(label, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val ring = compose.onNode(SemanticsMatcher.expectValue(RingToneKey, RingTone.SAFETY)).fetchSemanticsNode().boundsInRoot
        val disc = with(compose.density) { ringInnerDiameter(264.dp).toPx() }
        val cx = ring.center.x
        val cy = ring.center.y
        val corner = kotlin.math.hypot(bounds.right - cx, maxOf(bounds.bottom - cy, cy - bounds.top))
        assertTrue("corner ${'$'}corner outside disc radius ${'$'}{disc / 2}", corner <= disc / 2 + 1f)
    }

    /** Every row, scaled about its slot centre, stays inside its slot and keeps its outer corners inside the circle. */
    private fun assertInside(fit: CircleFit, widths: List<Float>, heights: List<Float>, diameter: Float) {
        var top = -fit.slots.sum() / 2f
        widths.indices.forEach { i ->
            val h = heights[i] * fit.scales[i]
            val centre = top + fit.slots[i] / 2f
            val corner = kotlin.math.hypot(widths[i] * fit.scales[i] / 2f, kotlin.math.abs(centre) + h / 2f)
            assertTrue("row " + i + " corner " + corner, corner <= diameter / 2f + 0.01f)
            assertTrue("row " + i + " fits its slot", h <= fit.slots[i] + 0.01f)
            top += fit.slots[i]
        }
    }

    @Test
    fun `a wide line at the rim shrinks on its own and the number keeps scale 1 (I2)`() {
        // number 80 x 100, label 140 x 24, wide date line 200 x 20 in a 194 disc
        val widths = listOf(80f, 140f, 200f)
        val heights = listOf(100f, 24f, 20f)
        assertTrue(circleFitScale(widths, heights, 194f) < 1f) // uniform would shrink the number too
        val fit = circleFitRows(widths, heights, 194f)
        assertEquals(1f, fit.scales[0], 0f)
        assertTrue(fit.scales[2] < 1f)
        assertInside(fit, widths, heights, 194f)
        // everything fits: nothing scales
        assertEquals(listOf(1f, 1f), circleFitRows(listOf(60f, 100f), listOf(60f, 20f), 194f).scales)
        // the stack is too tall for the number to stay: it gives way too, still inside
        val tallW = listOf(80f, 150f, 150f)
        val tallH = listOf(170f, 30f, 30f)
        val tall = circleFitRows(tallW, tallH, 194f)
        assertTrue(tall.scales[0] < 1f)
        assertInside(tall, tallW, tallH, 194f)
        // a tall 4-row stack where a smaller text cap keeps the number whole (I-C): it must not settle for a shrunk number
        val fourW = listOf(87f, 178f, 184f, 96f)
        val fourH = listOf(103f, 13f, 23f, 22f)
        val four = circleFitRows(fourW, fourH, 175f)
        assertEquals(1f, four.scales[0], 0f)
        assertInside(four, fourW, fourH, 175f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text metrics
    fun `at font scale 1_3 the number keeps its size and only the text lines shrink (I2)`() {
        val ring = 260.dp
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                NobbyTheme(ThemeSpec()) {
                    // Rows like Home's RingCenter: the number is sized in dp (it ignores the font scale) with its box
                    // trimmed; a long date line sits at the rim.
                    val numberSp = with(LocalDensity.current) { (96.dp * (ring / 264.dp)).toSp() }
                    val number = NobbyType.countdown.copy(fontSize = numberSp, lineHeight = numberSp)
                    val sub = MaterialTheme.typography.bodyMedium
                    Column {
                        BeadRing(RingTone.WEAR, beads = 21, done = 8, modifier = Modifier.size(ring)) {
                            CircleFitColumn {
                                Text("13", Modifier.trimLineBox().testTag("number"), style = number, maxLines = 1)
                                Text("Tage bis raus", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
                                Text("Mi., 12. Nov. · 21:00", Modifier.testTag("sub"), style = sub, maxLines = 1, softWrap = false)
                            }
                        }
                        Text("13", Modifier.trimLineBox().testTag("numberRef"), style = number, maxLines = 1)
                        Text("Mi., 12. Nov. · 21:00", Modifier.testTag("subRef"), style = sub, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val number = bounds("number")
        val sub = bounds("sub")
        assertEquals("number keeps its full size", bounds("numberRef").height, number.height, 1f)
        val subRef = bounds("subRef")
        assertTrue("sub-line shrinks: " + sub.height + " vs " + subRef.height, sub.height < subRef.height - 1f)
        val disc = compose.onNode(SemanticsMatcher.expectValue(RingToneKey, RingTone.WEAR)).fetchSemanticsNode().boundsInRoot
        val r = with(compose.density) { ringInnerDiameter(ring).toPx() } / 2f
        listOf(number, sub).forEach { b ->
            val corner = kotlin.math.hypot(maxOf(b.right - disc.center.x, disc.center.x - b.left), maxOf(b.bottom - disc.center.y, disc.center.y - b.top))
            assertTrue("corner " + corner + " outside disc radius " + r, corner <= r + 1f)
        }
    }
}
