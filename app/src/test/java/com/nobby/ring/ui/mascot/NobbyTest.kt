package com.nobby.ring.ui.mascot

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NobbyTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `nobby exposes mood and prop and grows by the prop extents`() {
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                Column {
                    Nobby(MascotMood.WORRIED, 80.dp, Modifier.testTag("plain"))
                    Nobby(MascotMood.HAPPY, 80.dp, Modifier.testTag("wave"), prop = MascotProp.WAVE)
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.WORRIED)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.WAVE)).assertExists()
        compose.onNodeWithTag("plain").assertWidthIsEqualTo(80.dp).assertHeightIsEqualTo(80.dp)
        compose.onNodeWithTag("wave").assertWidthIsAtLeast(90.dp) // 80 * (136 + 18) / 136 = 90.6
    }
}
