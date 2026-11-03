package com.nobby.ring.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.ui.theme.roles
import androidx.glance.color.ColorProvider as DayNightColorProvider

/** Solid widget colours of one preset in one mode (bundle components/widget.html, generalised to every preset). */
data class WidgetColors(
    val bg: Color,
    val fg: Color,
    val sub: Color,
    val hi: Color,
    val onHi: Color,
    val hiContainer: Color,
    val onHiContainer: Color,
    val err: Color,
    val onErr: Color,
    val errContainer: Color,
    val onErrContainer: Color,
    val track: Color,
    val pause: Color,
    val pauseContainer: Color,
    val mascotBody: Color,
    val mascotCheek: Color,
    val ink: Color,
)

/** Background = surface (light) / surfaceContainer (dark); track = surfaceContainerHigh / Highest. */
fun Palette.widgetColors(dark: Boolean): WidgetColors {
    val r = roles(dark)
    return WidgetColors(
        bg = if (dark) r.surfaceContainer else r.surface,
        fg = r.onSurface,
        sub = r.onSurfaceVariant,
        hi = r.primary,
        onHi = r.onPrimary,
        hiContainer = r.primaryContainer,
        onHiContainer = r.onPrimaryContainer,
        err = r.error,
        onErr = r.onError,
        errContainer = r.errorContainer,
        onErrContainer = r.onErrorContainer,
        track = if (dark) r.surfaceContainerHighest else r.surfaceContainerHigh,
        pause = r.secondary,
        pauseContainer = r.secondaryContainer,
        mascotBody = r.mascot,
        mascotCheek = r.mascotCheek,
        ink = r.ink,
    )
}

/** Day/night pairs follow the system for ThemeMode.SYSTEM; a forced Light/Dark mode pins one colour. */
class WidgetPalette(private val light: WidgetColors, private val dark: WidgetColors, private val mode: ThemeMode) {
    fun of(pick: (WidgetColors) -> Color): ColorProvider = when (mode) {
        ThemeMode.SYSTEM -> DayNightColorProvider(day = pick(light), night = pick(dark))
        ThemeMode.LIGHT -> ColorProvider(pick(light))
        ThemeMode.DARK -> ColorProvider(pick(dark))
    }
}
