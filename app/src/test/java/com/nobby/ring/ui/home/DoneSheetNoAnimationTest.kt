package com.nobby.ring.ui.home

import android.provider.Settings
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * "Remove animations" (animator duration scale 0): Compose finishes every animation on the next frame, so the 4 s
 * auto-close must come from a real delay and not from the end of the countdown animation.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DoneSheetNoAnimationTest {
    private object NoAnimations : MotionDurationScale {
        override val scaleFactor: Float = 0f
    }

    @get:Rule val compose = createComposeRule(effectContext = NoAnimations)

    private val celebration = Celebration(EventKind.REMOVE, EventKind.INSERT, LocalDateTime(2026, 11, 29, 21, 0))

    @Before
    fun animationsOff() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @Test
    fun `with animations off the sheet still stays up for four seconds`() {
        compose.mainClock.autoAdvance = false
        var dismissed = 0
        compose.setContent { NobbyTheme(ThemeSpec()) { DoneSheet(celebration, onDismiss = { dismissed++ }) } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(DONE_SHEET_TAG).assertExists()
        assertEquals(0, dismissed)
        compose.mainClock.advanceTimeBy(AUTO_DISMISS_MS - 1_500)
        assertEquals(0, dismissed)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(1, dismissed)
    }
}
