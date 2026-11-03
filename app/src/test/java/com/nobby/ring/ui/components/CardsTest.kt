package com.nobby.ring.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val planned = LocalDateTime(2026, 11, 22, 21, 0)

    private fun show(content: @Composable () -> Unit) = compose.setContent { NobbyTheme(ThemeSpec()) { content() } }

    @Test
    fun `next event card counts down in days`() {
        show { NextEventCard(EventKind.REMOVE, planned, daysUntil = 10) }
        compose.onNodeWithText("Remove ring").assertExists()
        compose.onNodeWithText("in 10 days", substring = true).assertExists()
    }

    @Test
    fun `due card says today and shows its actions`() {
        show { NextEventCard(EventKind.REMOVE, planned, daysUntil = 0, actions = { Text("Done") }) }
        compose.onNodeWithText("Today: Ring out at", substring = true).assertExists()
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `overdue card offers done and snooze`() {
        var done = 0
        var snoozed = 0
        show {
            OverdueCard(EventKind.REMOVE, planned, "2 days overdue", "Protection may be reduced", { done++ }, { snoozed++ }, enabled = true)
        }
        compose.onNodeWithText("2 days overdue").assertExists()
        compose.onNodeWithText("Protection may be reduced").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Snooze").performClick()
        assertEquals(1, done)
        assertEquals(1, snoozed)
    }

    @Test
    fun `settings row is clickable and shows its summary`() {
        var opened = 0
        show { SettingsGroup("Ring") { SettingsRow(Icons.Rounded.Schedule, "Cycle & schedule", summary = "21 + 7 days · 21:00", onClick = { opened++ }) } }
        compose.onNodeWithText("21 + 7 days · 21:00", substring = true).assertExists()
        compose.onNodeWithText("Cycle & schedule", substring = true).performClick()
        assertEquals(1, opened)
    }
}
