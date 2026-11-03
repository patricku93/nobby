package com.nobby.ring.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.MascotMoodKey
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.update.UpdateResult
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeContentTest {
    @get:Rule val compose = createComposeRule()

    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private val celebration = Celebration(EventKind.REMOVE, EventKind.INSERT, LocalDateTime(2026, 11, 29, 21, 0))

    private fun state(now: LocalDateTime, logs: List<EventLog> = emptyList()) = ScheduleRepository.buildState(config, logs, now)
    private fun mood(m: MascotMood) = SemanticsMatcher.expectValue(MascotMoodKey, m)

    private fun showRing(now: LocalDateTime, logs: List<EventLog> = emptyList()) = compose.setContent {
        NobbyTheme(ThemeSpec()) { HomeContent(HomeUiState(state(now, logs)), exactAlarmsAllowed = true, notificationsEnabled = true, actions = HomeActions()) }
    }

    @Test fun `wear shows a happy sticker`() { showRing(LocalDateTime(2026, 11, 12, 12, 0)); compose.onNode(mood(MascotMood.HAPPY)).assertExists() }

    @Test fun `due today shows a neutral sticker`() { showRing(LocalDateTime(2026, 11, 22, 10, 0)); compose.onNode(mood(MascotMood.NEUTRAL)).assertExists() }

    @Test fun `overdue shows a worried sticker`() { showRing(LocalDateTime(2026, 11, 24, 22, 0)); compose.onNode(mood(MascotMood.WORRIED)).assertExists() }

    @Test fun `safety threshold shows an alert sticker`() { showRing(LocalDateTime(2026, 11, 30, 22, 0)); compose.onNode(mood(MascotMood.ALERT)).assertExists() }

    @Test fun `pause shows a sleepy sticker`() { showRing(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed)); compose.onNode(mood(MascotMood.SLEEPY)).assertExists() }

    @Test
    fun `done sheet replaces the ring sticker and closes on tap`() {
        compose.mainClock.autoAdvance = false // the 4 s auto-close would otherwise elapse while the test waits for idle
        var shown by mutableStateOf<Celebration?>(celebration)
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                HomeContent(
                    HomeUiState(state(LocalDateTime(2026, 11, 22, 21, 30), listOf(removed)), celebration = shown),
                    exactAlarmsAllowed = true, notificationsEnabled = true,
                    actions = HomeActions(onDismissCelebration = { shown = null }),
                )
            }
        }
        compose.mainClock.advanceTimeBy(600) // let the sheet slide in so the tap lands on it
        compose.onNodeWithTag(DONE_SHEET_TAG).assertExists()
        compose.onNode(mood(MascotMood.YAY)).assertExists()
        compose.onNode(mood(MascotMood.SLEEPY)).assertDoesNotExist() // one mascot per screen
        compose.onNodeWithText("Yay, ring's out!").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(DONE_SHEET_TAG).assertDoesNotExist()
        compose.onNode(mood(MascotMood.SLEEPY)).assertExists()
    }

    @Test
    fun `done sheet closes by itself after four seconds`() {
        compose.mainClock.autoAdvance = false
        var shown by mutableStateOf<Celebration?>(celebration)
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                HomeContent(
                    HomeUiState(state(LocalDateTime(2026, 11, 22, 21, 30), listOf(removed)), celebration = shown),
                    exactAlarmsAllowed = true, notificationsEnabled = true,
                    actions = HomeActions(onDismissCelebration = { shown = null }),
                )
            }
        }
        compose.onNodeWithTag(DONE_SHEET_TAG).assertExists()
        compose.mainClock.advanceTimeBy(AUTO_DISMISS_MS + 1_000)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(DONE_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun `overdue remove still offers skip pause and tapping it fires the action`() {
        var toggled = 0
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                HomeContent(
                    HomeUiState(state(LocalDateTime(2026, 11, 24, 22, 0))),
                    exactAlarmsAllowed = true, notificationsEnabled = true,
                    actions = HomeActions(onToggleSkipPause = { toggled++ }),
                )
            }
        }
        compose.onNodeWithText("Skip break").performScrollTo().performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun `overdue swap offers keep the break and tapping it fires the action`() {
        var toggled = 0
        val skipping = ScheduleRepository.buildState(config.copy(skipPauseOnce = true), emptyList(), LocalDateTime(2026, 11, 24, 22, 0))
        compose.setContent {
            NobbyTheme(ThemeSpec()) {
                HomeContent(HomeUiState(skipping), exactAlarmsAllowed = true, notificationsEnabled = true, actions = HomeActions(onToggleSkipPause = { toggled++ }))
            }
        }
        compose.onNodeWithText("Keep the break").performScrollTo().performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun `pause does not offer skip break`() {
        showRing(LocalDateTime(2026, 11, 25, 12, 0), listOf(removed))
        compose.onNodeWithText("Skip break").assertDoesNotExist()
        compose.onNodeWithText("Keep the break").assertDoesNotExist()
    }

    @Test
    fun `removing the done sheet from composition dismisses the celebration`() {
        compose.mainClock.autoAdvance = false
        var show by mutableStateOf(true)
        var dismissed = 0
        compose.setContent {
            NobbyTheme(ThemeSpec()) { if (show) DoneSheet(celebration, onDismiss = { dismissed++ }) }
        }
        assertEquals(0, dismissed)
        show = false
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    private val offer = UpdateResult.Available("2.1.0", "https://github.com/p/n/releases/tag/v2.1.0", null)

    private fun showUpdate(exactAlarms: Boolean, notifications: Boolean) = compose.setContent {
        NobbyTheme(ThemeSpec()) {
            HomeContent(
                HomeUiState(state(LocalDateTime(2026, 11, 12, 12, 0))),
                exactAlarmsAllowed = exactAlarms, notificationsEnabled = notifications,
                actions = HomeActions(), update = offer,
            )
        }
    }

    @Test
    fun `update banner shows when no permission banner shows`() {
        showUpdate(exactAlarms = true, notifications = true)
        compose.onNodeWithText("NoBby 2.1.0 is available").assertExists()
    }

    @Test
    fun `update banner shows next to a single permission banner`() {
        showUpdate(exactAlarms = false, notifications = true)
        compose.onNodeWithText("NoBby 2.1.0 is available").assertExists()
        compose.onNodeWithText("Exact alarms are off. Reminders may be late.").assertExists()
    }

    @Test
    fun `update banner is hidden when both permission banners show`() {
        showUpdate(exactAlarms = false, notifications = false)
        compose.onNodeWithText("Exact alarms are off. Reminders may be late.").assertExists()
        compose.onNodeWithText("Notifications are off. Reminders won't show.").assertExists()
        compose.onNodeWithText("NoBby 2.1.0 is available").assertDoesNotExist()
    }
}
