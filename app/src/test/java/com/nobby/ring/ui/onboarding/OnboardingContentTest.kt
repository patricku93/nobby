package com.nobby.ring.ui.onboarding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobby.ring.domain.engine.SetupStatus
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
    private val base = OnboardingState(statusDate = LocalDate(2026, 11, 1))

    private fun show(state: OnboardingState, actions: OnboardingActions = OnboardingActions()) =
        compose.setContent { NobbyTheme(ThemeSpec()) { OnboardingContent(state, actions) } }

    private fun prop(p: MascotProp) = SemanticsMatcher.expectValue(MascotPropKey, p)

    @Test
    fun `step 1 waves hello and asks where you are`() {
        show(base)
        compose.onNode(prop(MascotProp.WAVE)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.HAPPY)).assertExists()
        compose.onNodeWithText("Hi! · Step 1 of 4").assertExists()
        compose.onNodeWithText("Where are you right now?").assertExists()
        compose.onAllNodes(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.WAVE)).assertCountEquals(1)
    }

    @Test
    fun `the three answers are radio rows and Next waits for one`() {
        show(base)
        listOf("Ring is in", "Ring is out (break)", "Not started yet").forEach { compose.onNodeWithText(it).assertExists() }
        listOf(SetupStatus.RING_IN, SetupStatus.RING_OUT, SetupStatus.NOT_STARTED).forEach {
            compose.onNodeWithTag("status_${it.name}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assertIsNotSelected()
        }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("When did you insert it?").assertDoesNotExist()
    }

    @Test
    fun `tapping anywhere on a row picks that answer`() {
        var picked: SetupStatus? = null
        show(base, OnboardingActions(onStatus = { picked = it }))
        compose.onNodeWithText("Ring is out (break)").performClick() // the label, not the radio dot
        assertEquals(SetupStatus.RING_OUT, picked)
        compose.onNodeWithTag("status_NOT_STARTED").performClick()
        assertEquals(SetupStatus.NOT_STARTED, picked)
    }

    @Test
    fun `each answer asks its own question and Next goes on`() {
        var next = 0
        val state = mutableStateOf(base.copy(status = SetupStatus.RING_IN))
        compose.setContent { NobbyTheme(ThemeSpec()) { OnboardingContent(state.value, OnboardingActions(onNext = { next++ })) } }
        compose.onNodeWithTag("status_RING_IN").assertIsSelected()
        compose.onNodeWithText("When did you insert it?").assertExists()
        state.value = base.copy(status = SetupStatus.RING_OUT)
        compose.onNodeWithText("When did you take it out?").assertExists()
        state.value = base.copy(status = SetupStatus.NOT_STARTED)
        compose.onNodeWithText("When will you insert the first ring?").assertExists()
        compose.onNodeWithText("Next").assertIsEnabled().performClick()
        assertEquals(1, next)
    }

    @Test
    fun `a future time is flagged and blocks Next`() {
        show(base.copy(status = SetupStatus.RING_IN, statusIssue = StatusIssue.IN_FUTURE))
        compose.onNodeWithText("That's in the future. Pick a time up to now.").performScrollTo().assertExists()
        compose.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun `a break that is already over says so and asks for the insertion`() {
        show(base.copy(status = SetupStatus.NOT_STARTED, pauseOver = true))
        compose.onNodeWithText("Your break is already over – when will you insert the new ring?").assertExists()
        compose.onNodeWithText("When will you insert the first ring?").performScrollTo().assertExists()
    }

    @Test
    fun `step 2 asks for the rhythm and holds the calendar`() {
        show(base.copy(step = 1))
        compose.onNode(prop(MascotProp.CALENDAR)).assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotMoodKey, MascotMood.NEUTRAL)).assertExists()
        compose.onNodeWithText("Your rhythm").assertExists()
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
