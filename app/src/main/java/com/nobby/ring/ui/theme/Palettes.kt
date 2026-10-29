package com.nobby.ring.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.nobby.ring.domain.model.Palette

private data class Seed(val primary: Color, val primaryDark: Color, val secondary: Color, val tertiary: Color, val bgLight: Color, val bgDark: Color)

private val seeds = mapOf(
    Palette.ROSE to Seed(Color(0xFFD81B60), Color(0xFFF48FB1), Color(0xFFAD1457), Color(0xFFFF8A80), Color(0xFFFFF1F5), Color(0xFF1C1114)),
    Palette.LAVENDER to Seed(Color(0xFF7E57C2), Color(0xFFB39DDB), Color(0xFF5E35B1), Color(0xFF9FA8DA), Color(0xFFF5F2FB), Color(0xFF15121C)),
    Palette.MINT to Seed(Color(0xFF00796B), Color(0xFF80CBC4), Color(0xFF00897B), Color(0xFFA5D6A7), Color(0xFFEFFAF7), Color(0xFF0F1A18)),
    Palette.OCEAN to Seed(Color(0xFF1565C0), Color(0xFF90CAF9), Color(0xFF1565C0), Color(0xFF80DEEA), Color(0xFFEEF5FC), Color(0xFF0F151C)),
    Palette.GRAPHITE to Seed(Color(0xFF455A64), Color(0xFFB0BEC5), Color(0xFF263238), Color(0xFF90A4AE), Color(0xFFF4F6F7), Color(0xFF121415)),
)

private val LightInk = Color(0xFF1C1C1E)
private val LightInkVariant = Color(0xFF49454F)
private val DarkInk = Color(0xFFECE6E8)
private val DarkInkVariant = Color(0xFFCAC4D0)

/** Blends this colour at the given alpha over an opaque background, yielding an opaque colour. */
private fun Color.over(bg: Color, alpha: Float): Color = copy(alpha = alpha).compositeOver(bg)

fun Palette.accent(dark: Boolean): Color = seeds.getValue(this).let { if (dark) it.primaryDark else it.primary }

fun paletteSchemes(palette: Palette): Pair<ColorScheme, ColorScheme> {
    val s = seeds.getValue(palette)
    val light = lightColorScheme(
        primary = s.primary,
        onPrimary = Color.White,
        primaryContainer = s.primaryDark.over(s.bgLight, 0.35f),
        onPrimaryContainer = LightInk,
        secondary = s.secondary,
        onSecondary = Color.White,
        secondaryContainer = s.primaryDark.over(s.bgLight, 0.30f),
        onSecondaryContainer = LightInk,
        tertiary = s.tertiary,
        onTertiary = LightInk,
        tertiaryContainer = s.tertiary.over(s.bgLight, 0.30f),
        onTertiaryContainer = LightInk,
        background = s.bgLight,
        onBackground = LightInk,
        surface = s.bgLight,
        onSurface = LightInk,
        surfaceVariant = s.primaryDark.over(s.bgLight, 0.18f),
        onSurfaceVariant = LightInkVariant,
        surfaceContainerLow = s.primary.over(s.bgLight, 0.04f),
        surfaceContainer = s.primary.over(s.bgLight, 0.06f),
        surfaceContainerHigh = s.primary.over(s.bgLight, 0.09f),
        surfaceContainerHighest = s.primary.over(s.bgLight, 0.12f),
        error = Color(0xFFB3261E),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )
    val dark = darkColorScheme(
        primary = s.primaryDark,
        onPrimary = s.bgDark,
        primaryContainer = s.secondary.over(s.bgDark, 0.45f),
        onPrimaryContainer = s.primaryDark,
        secondary = s.tertiary,
        onSecondary = s.bgDark,
        secondaryContainer = s.tertiary.over(s.bgDark, 0.40f),
        onSecondaryContainer = DarkInk,
        tertiary = s.tertiary,
        onTertiary = s.bgDark,
        tertiaryContainer = s.tertiary.over(s.bgDark, 0.30f),
        onTertiaryContainer = s.tertiary,
        background = s.bgDark,
        onBackground = DarkInk,
        surface = s.bgDark,
        onSurface = DarkInk,
        surfaceVariant = s.primaryDark.over(s.bgDark, 0.14f),
        onSurfaceVariant = DarkInkVariant,
        surfaceContainerLow = s.primaryDark.over(s.bgDark, 0.04f),
        surfaceContainer = s.primaryDark.over(s.bgDark, 0.06f),
        surfaceContainerHigh = s.primaryDark.over(s.bgDark, 0.09f),
        surfaceContainerHighest = s.primaryDark.over(s.bgDark, 0.12f),
        error = Color(0xFFF2B8B5),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )
    return light to dark
}
