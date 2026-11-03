package com.nobby.ring.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.nobby.ring.ui.theme.NobbyMotion
import com.nobby.ring.ui.theme.reducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RingTone { WEAR, DUE, OVERDUE, SAFETY, PAUSE }
enum class BeadKind { PAST, CURRENT, FUTURE }

/** Above this many days the bracelet becomes a plain progress arc (bundle: "arc fallback above 35 beads"). */
const val MAX_BEADS = 35

val RingToneKey = SemanticsPropertyKey<RingTone>("RingTone")
var SemanticsPropertyReceiver.ringTone by RingToneKey

/** One bead per day: [done] beads filled, the bead at index [done] (or the last one when all are done) is today. */
fun beadKinds(beads: Int, done: Int): List<BeadKind> = List(beads) { i ->
    when {
        i == done || (done >= beads && i == beads - 1) -> BeadKind.CURRENT
        i < done -> BeadKind.PAST
        else -> BeadKind.FUTURE
    }
}

/**
 * Diameter of the free disc inside the beads: the inner edge of today's (largest) bead, see [drawRing]
 * (track width 8.5 %, 4 dp inset, current bead radius 0.62 x track width), less a 6 dp margin on each side.
 */
fun ringInnerDiameter(ringSize: Dp): Dp = (ringSize * 0.81f - 20.dp).coerceAtLeast(0.dp)

/**
 * Scale (at most 1) at which a centred stack of rows ([widths] x [heights], top to bottom) fits inside a circle of
 * [diameter] when scaled about the circle's centre: every row's outer corners must stay inside the circle, so a wide
 * line near the middle may be wider than a line near the rim.
 */
fun circleFitScale(widths: List<Float>, heights: List<Float>, diameter: Float): Float {
    val r = diameter / 2f
    var top = -heights.sum() / 2f
    var scale = 1f
    widths.zip(heights).forEach { (w, h) ->
        val bottom = top + h
        val corner = hypot(w / 2f, maxOf(abs(top), abs(bottom)))
        if (corner > 0f) scale = minOf(scale, r / corner)
        top = bottom
    }
    return scale
}

/**
 * Column for the ring centre: each child is measured at its natural size (keep lines single-line), the stack is
 * centred and scaled down uniformly with [circleFitScale] so every line stays inside the circle inscribed in the
 * incoming bounds. Nothing wraps, nothing is clipped, nothing touches the beads.
 */
@Composable
fun CircleFitColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val rows = measurables.map { it.measure(Constraints()) }
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else rows.maxOfOrNull { it.width } ?: 0
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else rows.sumOf { it.height }
        val scale = circleFitScale(rows.map { it.width.toFloat() }, rows.map { it.height.toFloat() }, minOf(w, h).toFloat())
        layout(w, h) {
            var top = -rows.sumOf { it.height } / 2f
            rows.forEach { row ->
                val rowCentre = top + row.height / 2f
                val y = h / 2f + rowCentre * scale - row.height / 2f
                row.placeWithLayer((w - row.width) / 2, y.roundToInt()) {
                    scaleX = scale
                    scaleY = scale
                }
                top += row.height
            }
        }
    }
}

private data class Tones(val track: Color, val bead: Color)

private fun ColorScheme.tones(tone: RingTone): Tones = when (tone) {
    RingTone.WEAR -> Tones(surfaceContainerHigh, primary)
    RingTone.DUE -> Tones(primaryContainer, primary)
    RingTone.OVERDUE, RingTone.SAFETY -> Tones(errorContainer, error)
    RingTone.PAUSE -> Tones(secondaryContainer, secondary)
}

/**
 * The countdown bracelet (nobby.js ring()): a track circle plus one bead per day of the phase, today's bead
 * highlighted. [center] gets the free disc inside the beads ([ringInnerDiameter]); use [CircleFitColumn] for text so it
 * always fits; [sticker] sits on the rim top-right at 30 % size, rotated 8°.
 */
@Composable
fun BeadRing(
    tone: RingTone,
    beads: Int,
    done: Int,
    modifier: Modifier = Modifier,
    sticker: (@Composable (Dp) -> Unit)? = null,
    center: @Composable (Dp) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = reducedMotion()
    val fill by animateFloatAsState(
        targetValue = done.toFloat(),
        animationSpec = if (reduced) snap() else tween(NobbyMotion.LONG, easing = NobbyMotion.Decelerate),
        label = "beads",
    )
    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val ringSize = min(maxWidth, maxHeight)
        Canvas(Modifier.size(ringSize).semantics { ringTone = tone }) { drawRing(scheme, tone, beads.coerceAtLeast(1), fill) }
        Box(Modifier.size(ringInnerDiameter(ringSize)), contentAlignment = Alignment.Center) { center(ringSize) }
        if (sticker != null) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = ringSize * 0.06f, y = -ringSize * 0.05f).rotate(8f)) {
                sticker(ringSize * 0.3f)
            }
        }
    }
}

private fun DrawScope.drawRing(scheme: ColorScheme, tone: RingTone, beads: Int, fill: Float) {
    val t = scheme.tones(tone)
    val s = size.minDimension
    val tw = s * 0.085f
    val r = s / 2f - tw / 2f - 4.dp.toPx()
    val c = Offset(size.width / 2f, size.height / 2f)
    if (tone == RingTone.SAFETY) drawCircle(scheme.errorContainer.copy(alpha = 0.55f), r - tw / 2f - 10.dp.toPx(), c)
    drawCircle(t.track, r, c, style = Stroke(tw))
    if (beads > MAX_BEADS) {
        val sweep = 360f * (fill / beads).coerceIn(0f, 1f)
        drawArc(t.bead, -90f, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(tw * 0.58f, cap = StrokeCap.Round))
        return
    }
    val step = 360f / beads
    beadKinds(beads, fill.toInt()).forEachIndexed { i, kind ->
        val a = Math.toRadians((-90f + step * (i + 0.5f)).toDouble())
        val p = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        when (kind) {
            BeadKind.CURRENT -> {
                drawCircle(scheme.surfaceContainerLowest, tw * 0.62f, p)
                drawCircle(t.bead, tw * 0.44f, p)
            }
            BeadKind.PAST -> drawCircle(t.bead, tw * 0.29f, p)
            BeadKind.FUTURE -> drawCircle(scheme.outline.copy(alpha = 0.7f), tw * 0.14f, p)
        }
    }
}
