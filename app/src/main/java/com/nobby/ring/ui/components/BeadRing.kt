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
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.nobby.ring.ui.theme.NobbyMotion
import com.nobby.ring.ui.theme.reducedMotion
import kotlin.math.cos
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

private data class Tones(val track: Color, val bead: Color)

private fun ColorScheme.tones(tone: RingTone): Tones = when (tone) {
    RingTone.WEAR -> Tones(surfaceContainerHigh, primary)
    RingTone.DUE -> Tones(primaryContainer, primary)
    RingTone.OVERDUE, RingTone.SAFETY -> Tones(errorContainer, error)
    RingTone.PAUSE -> Tones(secondaryContainer, secondary)
}

/**
 * The countdown bracelet (nobby.js ring()): a track circle plus one bead per day of the phase, today's bead
 * highlighted. [center] is placed in the middle 66 %; [sticker] sits on the rim top-right at 30 % size, rotated 8°.
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
        Box(Modifier.size(ringSize * 0.66f), contentAlignment = Alignment.Center) { center(ringSize) }
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
