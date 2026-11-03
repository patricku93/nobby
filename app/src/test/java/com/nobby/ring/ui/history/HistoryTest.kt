package com.nobby.ring.ui.history

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotProp
import com.nobby.ring.ui.mascot.MascotPropKey
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class HistoryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `timing uses the 60 minute confirmation tolerance`() {
        assertEquals(HistoryTiming.ON_TIME, historyTiming(59.minutes))
        assertEquals(HistoryTiming.ON_TIME, historyTiming((-59).minutes))
        assertEquals(HistoryTiming.LATE, historyTiming(60.minutes))
        assertEquals(HistoryTiming.EARLY, historyTiming((-2).hours))
    }

    @Test
    fun `empty history shows NoBby with the note`() {
        compose.setContent { NobbyTheme(ThemeSpec()) { HistoryContent(emptyList()) } }
        compose.onNode(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.NOTE)).assertExists()
        compose.onNodeWithText("Nothing here yet").assertExists()
    }

    @Test
    fun `the first entry replaces the empty state`() {
        val e = HistoryEntry(EventKind.REMOVE, LocalDateTime(2026, 10, 11, 21, 0), LocalDateTime(2026, 10, 11, 22, 30), 90.minutes)
        compose.setContent { NobbyTheme(ThemeSpec()) { HistoryContent(listOf(e)) } }
        compose.onNodeWithText("Ring removed").assertExists()
        compose.onNodeWithText("1 h 30 min late").assertExists()
        compose.onNode(SemanticsMatcher.expectValue(MascotPropKey, MascotProp.NOTE)).assertDoesNotExist()
    }
}
