package com.nobby.ring.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Assert.assertEquals
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
}
