package com.nobby.ring.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nobby.ring.domain.model.Palette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Guards against hand edits of the generated tables. Skipped where the local design bundle is absent (set NOBBY_DESIGN_DIR to its folder to run it). */
@RunWith(RobolectricTestRunner::class)
class TokenDriftTest {
    @Test
    fun `generated preset tables equal tokens json`() {
        val file = File((System.getenv("NOBBY_DESIGN_DIR") ?: "../design") + "/bundle/tokens.json")
        assumeTrue("design bundle not present", file.exists())
        val root = JSONObject(file.readText())
        assertEquals(root.getString("\$version"), TOKENS_VERSION)
        val presets = root.getJSONObject("presets")
        for (p in Palette.entries) for (dark in listOf(false, true)) {
            val json = presets.getJSONObject(p.presetId).getJSONObject(if (dark) "dark" else "light")
            val roles = p.roles(dark).byName()
            assertEquals(json.length(), roles.size)
            roles.forEach { (name, color) -> assertEquals("${p.presetId}.$name", json.getString(name).uppercase(), color.hex()) }
        }
    }

    private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
}
