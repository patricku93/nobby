package com.nobby.ring.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette

/** NoBby roles outside Material 3, per preset and mode, from the same generated table as the ColorScheme. */
@Immutable
data class NobbyExtraColors(
    val dark: Boolean,
    /** Sticker accent: washi tape, sparkle, note dot (tokens `accent`, butter in berry). */
    val sticker: Color,
    val mascotBody: Color,
    val mascotCheek: Color,
    /** Die-cut sticker rim around the mascot and props (tokens `stickerRim`). */
    val mascotRim: Color,
    /** Cocoa line colour of the mascot and doodles (tokens `ink`, cocoa in both modes). */
    val ink: Color,
    val shadow: Color,
    val overdue: Color,
    val overdueContainer: Color,
)

fun PresetRoles.extraColors(dark: Boolean): NobbyExtraColors = NobbyExtraColors(
    dark = dark,
    sticker = accent,
    mascotBody = mascot,
    mascotCheek = mascotCheek,
    mascotRim = stickerRim,
    ink = ink,
    shadow = shadow,
    overdue = error,
    overdueContainer = errorContainer,
)

fun Palette.extraColors(dark: Boolean): NobbyExtraColors = roles(dark).extraColors(dark)

val LocalNobbyExtraColors = staticCompositionLocalOf { Palette.BERRY.extraColors(dark = false) }

/** Access point for NoBby extras, next to MaterialTheme: `NobbyTheme.extra.sticker`. */
object NobbyTheme {
    val extra: NobbyExtraColors
        @Composable @ReadOnlyComposable get() = LocalNobbyExtraColors.current
}
