package com.nobby.ring.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Confetti for the Done sheet only (one doodle). Decorative. */
@Composable
fun DoneBurst(modifier: Modifier = Modifier) {
    val colors = mascotColors()
    val paths = remember { MascotPaths() }
    Canvas(modifier.aspectRatio(320f / 170f)) { drawBurst(colors, paths) }
}
