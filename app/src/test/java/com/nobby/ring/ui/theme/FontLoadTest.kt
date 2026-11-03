package com.nobby.ring.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FontLoadTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `both bundled variable fonts load and render`() {
        compose.setContent {
            MaterialTheme(typography = NobbyTypography) {
                Column {
                    Text("21", style = NobbyType.countdown)
                    Text("Beipackzettel ß ä ö ü", style = MaterialTheme.typography.bodyLarge)
                    Text("Erledigt", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        compose.onNodeWithText("21").assertIsDisplayed()
        compose.onNodeWithText("Beipackzettel ß ä ö ü").assertIsDisplayed()
        compose.onNodeWithText("Erledigt").assertIsDisplayed()
    }
}
