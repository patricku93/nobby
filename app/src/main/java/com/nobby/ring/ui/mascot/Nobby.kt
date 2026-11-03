package com.nobby.ring.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.nobby.ring.ui.mascot.MascotGeometry.VIEWBOX

/**
 * NoBby with a sticker rim (or [flat]). [size] is the base box (body + rim padding); a [prop] adds its extents.
 * Decorative: no content description. Colours follow the current preset and mode.
 */
@Composable
fun Nobby(mood: MascotMood, size: Dp, modifier: Modifier = Modifier, prop: MascotProp? = null, flat: Boolean = false) {
    val colors = mascotColors()
    val paths = remember { MascotPaths() }
    val ext = MascotGeometry.extents(prop)
    val width = size * ((VIEWBOX + ext[0] + ext[2]) / VIEWBOX)
    val height = size * ((VIEWBOX + ext[1] + ext[3]) / VIEWBOX)
    Canvas(
        modifier
            .size(width, height)
            .semantics {
                mascotMood = mood
                if (prop != null) mascotProp = prop
            },
    ) { drawNobby(mood, prop, flat, size.value, colors, paths) }
}
