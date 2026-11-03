package com.nobby.ring.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nobby.ring.domain.model.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class FormattingTest {
    @get:Rule val compose = createComposeRule()
    private val sunday = LocalDate(2026, 11, 8)

    @Test
    @Config(qualifiers = "de")
    fun `short date in german has day and abbreviated month`() {
        val s = formatShortDate(sunday)
        assertTrue(s, s.contains("8"))
        assertTrue(s, s.contains("Nov"))
        assertTrue(s, s.startsWith("So"))
    }

    @Test
    @Config(qualifiers = "en")
    fun `short date in english has day and abbreviated month`() {
        val s = formatShortDate(sunday)
        assertTrue(s, s.contains("8"))
        assertTrue(s, s.contains("Nov"))
        assertTrue(s, s.startsWith("Sun"))
    }

    @Test
    @Config(qualifiers = "de")
    fun `long date in german spells weekday and month`() {
        val s = formatLongDate(sunday)
        assertTrue(s, s.contains("Sonntag"))
        assertTrue(s, s.contains("November"))
    }

    @Test
    @Config(qualifiers = "en")
    fun `long date in english spells weekday and month`() {
        val s = formatLongDate(sunday)
        assertTrue(s, s.contains("Sunday"))
        assertTrue(s, s.contains("November"))
    }

    @Test
    fun `date time joins short date and time with a middle dot`() {
        val dt = LocalDateTime(2026, 11, 8, 21, 0)
        val s = formatDateTime(dt)
        assertTrue(s, s.startsWith(formatShortDate(dt.date)))
        assertTrue(s, s.contains(" \u00B7 "))
        assertTrue(s, s.contains("21") || s.contains("9"))
    }

    @Test
    @Config(qualifiers = "en")
    fun `theme mode labels resolve per mode in english`() {
        compose.setContent {
            Text(ThemeMode.SYSTEM.label())
            Text(ThemeMode.LIGHT.label())
            Text(ThemeMode.DARK.label())
        }
        compose.onNodeWithText("Auto").assertExists()
        compose.onNodeWithText("Light").assertExists()
        compose.onNodeWithText("Dark").assertExists()
    }

    @Test
    @Config(qualifiers = "de")
    fun `theme mode labels resolve per mode in german`() {
        compose.setContent {
            Text(ThemeMode.LIGHT.label())
            Text(ThemeMode.DARK.label())
        }
        compose.onNodeWithText("Hell").assertExists()
        compose.onNodeWithText("Dunkel").assertExists()
    }
}
