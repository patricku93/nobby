package com.nobby.ring.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette

/** tokens.json preset id of this palette (berry, lilac, peach, matcha, blueberry). */
val Palette.presetId: String get() = name.lowercase()

/** The 41 hand-specified roles of this preset in one mode, generated from tokens.json. Nothing is derived. */
fun Palette.roles(dark: Boolean): PresetRoles = PRESET_TABLE.getValue(presetId).let { if (dark) it.dark else it.light }

fun Palette.colorScheme(dark: Boolean): ColorScheme = roles(dark).toColorScheme(dark)

/** Swatch for compact pickers: the preset's primary in the given mode. */
fun Palette.swatch(dark: Boolean): Color = roles(dark).primary

/** Material 3 scheme with every role from the table; surfaceTint is transparent so elevation stays flat. */
fun PresetRoles.toColorScheme(dark: Boolean): ColorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    inversePrimary = inversePrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = inverseSurface,
    inverseOnSurface = inverseOnSurface,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    outline = outline,
    outlineVariant = outlineVariant,
    scrim = scrim,
    surfaceBright = surfaceBright,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceDim = surfaceDim,
)
