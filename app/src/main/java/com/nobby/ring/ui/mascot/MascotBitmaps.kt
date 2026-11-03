package com.nobby.ring.ui.mascot

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Off-screen renders of the mascot for places without Compose (notification large icon). */
object MascotBitmaps {
    /** The flat, rim-less, circle-safe face of nobby.js notifIcon() as a [px] x [px] bitmap. */
    fun face(mood: MascotMood, colors: MascotColors, px: Int): Bitmap {
        val image = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
            drawNobbyFace(mood, colors, MascotPaths())
        }
        return image.asAndroidBitmap()
    }
}
