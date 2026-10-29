package com.nobby.ring.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette

private data class Seed(val primary: Color, val primaryDark: Color, val secondary: Color, val tertiary: Color, val bgLight: Color, val bgDark: Color)

private val seeds = mapOf(
    Palette.ROSE to Seed(Color(0xFFD81B60), Color(0xFFF48FB1), Color(0xFFAD1457), Color(0xFFFF8A80), Color(0xFFFFF1F5), Color(0xFF1C1114)),
    Palette.LAVENDER to Seed(Color(0xFF7E57C2), Color(0xFFB39DDB), Color(0xFF5E35B1), Color(0xFF9FA8DA), Color(0xFFF5F2FB), Color(0xFF15121C)),
    Palette.MINT to Seed(Color(0xFF26A69A), Color(0xFF80CBC4), Color(0xFF00897B), Color(0xFFA5D6A7), Color(0xFFEFFAF7), Color(0xFF0F1A18)),
    Palette.OCEAN to Seed(Color(0xFF1E88E5), Color(0xFF90CAF9), Color(0xFF1565C0), Color(0xFF80DEEA), Color(0xFFEEF5FC), Color(0xFF0F151C)),
    Palette.GRAPHITE to Seed(Color(0xFF455A64), Color(0xFFB0BEC5), Color(0xFF263238), Color(0xFF90A4AE), Color(0xFFF4F6F7), Color(0xFF121415)),
)

fun Palette.accent(dark: Boolean): Color = seeds.getValue(this).let { if (dark) it.primaryDark else it.primary }

fun paletteSchemes(palette: Palette): Pair<ColorScheme, ColorScheme> {
    val s = seeds.getValue(palette)
    val light = lightColorScheme(
        primary = s.primary,
        onPrimary = Color.White,
        primaryContainer = s.primaryDark.copy(alpha = 0.35f),
        onPrimaryContainer = s.secondary,
        secondary = s.secondary,
        tertiary = s.tertiary,
        background = s.bgLight,
        surface = s.bgLight,
        surfaceVariant = s.primaryDark.copy(alpha = 0.18f),
        error = Color(0xFFB3261E),
    )
    val dark = darkColorScheme(
        primary = s.primaryDark,
        onPrimary = s.bgDark,
        primaryContainer = s.secondary.copy(alpha = 0.45f),
        onPrimaryContainer = s.primaryDark,
        secondary = s.primaryDark,
        tertiary = s.tertiary,
        background = s.bgDark,
        surface = s.bgDark,
        surfaceVariant = s.primaryDark.copy(alpha = 0.14f),
        error = Color(0xFFF2B8B5),
    )
    return light to dark
}
