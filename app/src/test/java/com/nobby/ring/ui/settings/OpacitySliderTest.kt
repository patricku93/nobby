package com.nobby.ring.ui.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class OpacitySliderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the slider is labelled for TalkBack and English writes the percent without a space`() {
        compose.setContent { NobbyTheme(ThemeSpec()) { OpacitySlider(50, {}, {}) } }
        compose.onNodeWithContentDescription("Background")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        compose.onNodeWithText("50%").assertExists()
    }
}
