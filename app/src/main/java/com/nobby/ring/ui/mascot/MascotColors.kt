package com.nobby.ring.ui.mascot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.PresetRoles
import com.nobby.ring.ui.theme.roles

/** Everything the painter needs, so every preset (and mode) gets its own mascot colours. */
@Immutable
data class MascotColors(
    val body: Color,
    val cheek: Color,
    val ink: Color,
    val rim: Color,
    /** Hard sticker drop: 14 % cocoa in light, 35 % black in dark (tokens.json elevation.sticker). */
    val drop: Color,
    val accent: Color,
    val primary: Color,
    val secondary: Color,
    val outline: Color,
)

private fun dropFor(dark: Boolean, ink: Color): Color = if (dark) Color.Black.copy(alpha = 0.35f) else ink.copy(alpha = 0.14f)

fun PresetRoles.mascotColors(dark: Boolean): MascotColors = MascotColors(
    body = mascot, cheek = mascotCheek, ink = ink, rim = stickerRim, drop = dropFor(dark, ink),
    accent = accent, primary = primary, secondary = secondary, outline = outline,
)

fun Palette.mascotColors(dark: Boolean): MascotColors = roles(dark).mascotColors(dark)

@Composable
@ReadOnlyComposable
fun mascotColors(): MascotColors {
    val c = MaterialTheme.colorScheme
    val x = NobbyTheme.extra
    return MascotColors(
        body = x.mascotBody, cheek = x.mascotCheek, ink = x.ink, rim = x.mascotRim, drop = dropFor(x.dark, x.ink),
        accent = x.sticker, primary = c.primary, secondary = c.secondary, outline = c.outline,
    )
}
