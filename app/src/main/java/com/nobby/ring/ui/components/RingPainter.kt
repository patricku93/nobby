package com.nobby.ring.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The five colour roles of the bead bracelet: the safety disc behind the ring, the track circle, the halo around
 * today's bead, the filled beads (and the arc above [MAX_BEADS]) and the small future dots.
 */
data class RingColors(val safetyDisc: Color, val track: Color, val halo: Color, val bead: Color, val future: Color)

/** Ring tones of the Home bracelet, taken from the preset's scheme. The widget uses the same mapping. */
fun ColorScheme.ringColors(tone: RingTone): RingColors {
    val (track, bead) = when (tone) {
        RingTone.WEAR -> surfaceContainerHigh to primary
        RingTone.DUE -> primaryContainer to primary
        RingTone.OVERDUE, RingTone.SAFETY -> errorContainer to error
        RingTone.PAUSE -> secondaryContainer to secondary
    }
    return RingColors(
        safetyDisc = errorContainer.copy(alpha = 0.55f),
        track = track,
        halo = surfaceContainerLowest,
        bead = bead,
        future = outline.copy(alpha = 0.7f),
    )
}

/**
 * The countdown bracelet (nobby.js ring()), drawn into [DrawScope.size]: a track circle plus one bead per day of the
 * phase, today's bead highlighted; above [MAX_BEADS] a plain progress arc. Shared by Home ([BeadRing]) and the widget
 * bitmap, so both always look the same. [fill] is the (animated) number of done days.
 */
fun DrawScope.drawRing(colors: RingColors, beads: Int, fill: Float, safety: Boolean) {
    val s = size.minDimension
    val tw = s * 0.085f
    val r = s / 2f - tw / 2f - 4.dp.toPx()
    val c = Offset(size.width / 2f, size.height / 2f)
    if (safety) drawCircle(colors.safetyDisc, r - tw / 2f - 10.dp.toPx(), c)
    drawCircle(colors.track, r, c, style = Stroke(tw))
    if (beads > MAX_BEADS) {
        val sweep = 360f * (fill / beads).coerceIn(0f, 1f)
        drawArc(colors.bead, -90f, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(tw * 0.58f, cap = StrokeCap.Round))
        return
    }
    val step = 360f / beads
    beadKinds(beads, fill.toInt()).forEachIndexed { i, kind ->
        val a = Math.toRadians((-90f + step * (i + 0.5f)).toDouble())
        val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        when (kind) {
            BeadKind.CURRENT -> {
                drawCircle(colors.halo, tw * 0.62f, p)
                drawCircle(colors.bead, tw * 0.44f, p)
            }
            BeadKind.PAST -> drawCircle(colors.bead, tw * 0.29f, p)
            BeadKind.FUTURE -> drawCircle(colors.future, tw * 0.14f, p)
        }
    }
}
