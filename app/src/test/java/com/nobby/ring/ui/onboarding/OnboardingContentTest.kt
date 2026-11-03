package com.nobby.ring.ui.onboarding

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotMoodKey
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.MascotPropKey
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingContentTest {
    @get:Rule val compose = createComposeRule()
    private val base = OnboardingState(anchorDate = LocalDate(2026, 11, 1))

    private fun show(state: OnboardingState, actions: OnboardingActions = OnboardingActions()) =
        compose.setContent { NobbyTheme(ThemeSpec()) { OnboardingContent(state, actions) } }

    private fun prop(p: MascotProp) = SemanticsMatcher.expectValue(MascotPropKey, p)

    @Test
    fun `step 1 waves hello`() {
        show(base)
        compose.onNode(prop(MascotProp.WAVE)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.HAPPY)).assertExists()
        compose.onNodeWithText("Hi! · Step 1 of 4").assertExists()
    }

    @Test
    fun `step 2 holds the calendar`() {
        show(base.copy(step = 1))
        compose.onNode(prop(MascotProp.CALENDAR)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.NEUTRAL)).assertExists()
    }

    @Test
    fun `step 4 shows the palette and picks a preset`() {
        var picked: Palette? = null
        show(base.copy(step = 3), OnboardingActions(onPalette = { picked = it }))
        compose.onNode(prop(MascotProp.PALETTE)).assertExists()
        compose.onNodeWithTag("preset_PEACH").performScrollTo().performClick()
        assertEquals(Palette.PEACH, picked)
        compose.onNodeWithText("Let's go").assertExists()
    }
}
