package com.nobby.ring.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.theme.NobbyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent { NobbyTheme(ThemeSpec()) { content() } }

    @Test
    fun `primary sticker button is at least 52 dp tall and clicks`() {
        var clicks = 0
        show { NobbyButton(onClick = { clicks++ }) { Text("Erledigt") } }
        compose.onNodeWithText("Erledigt").assertHeightIsAtLeast(52.dp).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `segmented choice exposes selection and switches`() {
        var picked by mutableStateOf(ThemeMode.LIGHT)
        show { SegmentedChoice(listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM), picked, { picked = it }, label = { it.name }) }
        compose.onNodeWithText("LIGHT").assertIsSelected()
        compose.onNodeWithText("DARK").assertIsNotSelected().performClick()
        compose.onNodeWithText("DARK").assertIsSelected()
        assertEquals(ThemeMode.DARK, picked)
    }

    @Test
    fun `picker field shows label and value as separate texts`() {
        var opened = false
        show { PickerField("Datum", "So., 8. Nov.", Icons.Rounded.CalendarMonth, onClick = { opened = true }) }
        compose.onNodeWithText("Datum", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("So., 8. Nov.", useUnmergedTree = true).assertExists()
        compose.onNode(hasClickAction()).performClick()
        assertTrue(opened)
    }

    @Test
    fun `number stepper respects its range`() {
        var value by mutableIntStateOf(1)
        show { NumberStepper(value, { value = it }, 1..3, "Pause") }
        compose.onNodeWithContentDescription("Less").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More").performClick()
        assertEquals(2, value)
    }

    @Test
    fun `phase chip renders its text`() {
        show { PhaseChip("Ring drin · Tag 12 von 21", ChipTone.WEAR) }
        compose.onNodeWithText("Ring drin · Tag 12 von 21").assertExists()
    }
}
