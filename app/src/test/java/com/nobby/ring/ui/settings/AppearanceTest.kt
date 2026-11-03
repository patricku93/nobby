package com.nobby.ring.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.roles
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tapping a preset switches the live theme`() {
        var spec by mutableStateOf(ThemeSpec())
        var primary = Color.Unspecified
        compose.setContent {
            NobbyTheme(spec) {
                primary = MaterialTheme.colorScheme.primary
                AppearanceContent(spec, onChange = { spec = it })
            }
        }
        compose.onNodeWithTag("preset_BERRY").assertIsSelected()
        compose.onNodeWithTag("preset_LILAC").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(Palette.LILAC, spec.palette)
        assertEquals(Palette.LILAC.roles(false).primary, primary)
        compose.onNodeWithTag("preset_LILAC").assertIsSelected()
        compose.onNodeWithTag("preset_BERRY").assertIsNotSelected()
    }

    @Test
    fun `mode chips switch between light, dark and auto`() {
        var spec by mutableStateOf(ThemeSpec())
        compose.setContent { NobbyTheme(spec) { AppearanceContent(spec, onChange = { spec = it }) } }
        compose.onNodeWithText("Auto").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.DARK, spec.mode)
    }
}
