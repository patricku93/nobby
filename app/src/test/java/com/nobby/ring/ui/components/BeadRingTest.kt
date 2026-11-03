package com.nobby.ring.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
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
}
