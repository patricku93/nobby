package com.nobby.ring.ui.theme

import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ContrastTest {
    private val pairs = listOf(
        Triple("onSurface", "surface", 4.5), Triple("onSurface", "background", 4.5),
        Triple("onSurface", "surfaceContainerLowest", 4.5), Triple("onSurface", "surfaceContainerHighest", 4.5),
        Triple("onSurfaceVariant", "surface", 4.5), Triple("onSurfaceVariant", "background", 4.5),
        Triple("onSurfaceVariant", "surfaceContainerHigh", 4.5), Triple("onSurfaceVariant", "surfaceContainerHighest", 4.5),
        Triple("onPrimary", "primary", 4.5), Triple("onPrimaryContainer", "primaryContainer", 4.5),
        Triple("onSecondary", "secondary", 4.5), Triple("onSecondaryContainer", "secondaryContainer", 4.5),
        Triple("onTertiary", "tertiary", 4.5), Triple("onTertiaryContainer", "tertiaryContainer", 4.5),
        Triple("onError", "error", 4.5), Triple("onErrorContainer", "errorContainer", 4.5),
        Triple("inverseOnSurface", "inverseSurface", 4.5),
        Triple("primary", "surface", 4.5), Triple("primary", "background", 4.5), Triple("primary", "surfaceContainer", 4.5),
        Triple("secondary", "background", 4.5), Triple("tertiary", "background", 4.5), Triple("error", "background", 4.5),
        Triple("onSurface", "primaryContainer", 4.5), Triple("onSurface", "secondaryContainer", 4.5),
        Triple("onSurface", "errorContainer", 4.5),
        Triple("primary", "primaryContainer", 3.0), Triple("secondary", "secondaryContainer", 3.0),
        Triple("error", "errorContainer", 3.0), Triple("outline", "background", 3.0),
    )

    @Test
    fun `the pair list is the design report's 30 pairs`() = assertEquals(30, pairs.size)

    @Test
    fun `every preset passes all 30 WCAG pairs in light and dark`() {
        val failures = buildList {
            for (p in Palette.entries) for (dark in listOf(false, true)) {
                val roles = p.roles(dark).byName()
                for ((fg, bg, need) in pairs) {
                    val r = contrast(roles.getValue(fg), roles.getValue(bg))
                    if (r < need) add("${p.presetId} ${if (dark) "dark" else "light"} $fg on $bg = ${"%.2f".format(r)} < $need")
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun channel(v: Float): Double = v.toDouble().let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }
}
