package com.nobby.ring.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.ui.components.RingColors
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.ringColors
import com.nobby.ring.ui.theme.colorScheme
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
    /** Home's ring colours per tone ([ringColors]), so the widget bracelet matches Home exactly. */
    val ringTones: Map<RingTone, RingColors> = emptyMap(),
) {
    fun ring(tone: RingTone): RingColors = ringTones.getValue(tone)

    /** The background with [opacityPercent] (0..100) as alpha; nothing else in the widget is ever translucent. */
    fun background(opacityPercent: Int): Color = bg.copy(alpha = opacityPercent.coerceIn(0, WidgetConfig.MAX_OPACITY) / 100f)
}

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
        ringTones = colorScheme(dark).let { scheme -> RingTone.entries.associateWith { scheme.ringColors(it) } },
    )
}

/** Day/night pairs follow the system for ThemeMode.SYSTEM; a forced Light/Dark mode pins one colour. */
class WidgetPalette(private val light: WidgetColors, private val dark: WidgetColors, private val mode: ThemeMode) {
    fun of(pick: (WidgetColors) -> Color): ColorProvider = when (mode) {
        ThemeMode.SYSTEM -> DayNightColorProvider(day = pick(light), night = pick(dark))
        ThemeMode.LIGHT -> ColorProvider(pick(light))
        ThemeMode.DARK -> ColorProvider(pick(dark))
    }

    fun background(opacityPercent: Int): ColorProvider = of { it.background(opacityPercent) }
}
