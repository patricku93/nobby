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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
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
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
 * Lays the countdown number out in [keep] of its measured height, centred, without clipping: the font's line box is
 * far taller than its figures (digits have no descender and stop at cap height), and that empty band would otherwise
 * take room from the lines below it inside the circle. The figures still draw in full inside the trimmed box.
 * Meant for [CircleFitColumn], which measures its rows with unbounded constraints; the trimmed height is coerced into
 * the incoming constraints so it also behaves under a minHeight elsewhere.
 */
internal fun Modifier.trimLineBox(keep: Float = RING_NUMBER_KEEP): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val h = (p.height * keep).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(p.width, h) { p.place(0, -(p.height - h) / 2) }
}

/**
 * Share of the number's line box that holds its figures, with a margin (Fredoka: about 0.58). Tuned to the font:
 * after a font change re-check it with BeadRingTest's font-scale 1.3 test and a Home screenshot.
 */
const val RING_NUMBER_KEEP = 0.7f

/** Per-row [scales] and the height of the slot each row is centred in; the slots are stacked and centred on the circle. */
data class CircleFit(val scales: List<Float>, val slots: List<Float>)

/** Text rows may shrink to this share (or to the uniform scale, if smaller) before the first row gives way. */
private const val MIN_TEXT_SCALE = 0.5f
private const val CAP_STEP = 0.05f
private const val FIT_PASSES = 6
private const val FIT_EPSILON = 1e-3f

/**
 * Fits a centred stack of rows into a circle of [diameter], keeping the first row (the countdown number) at full
 * size whenever it fits: every following row shrinks on its own to the chord at its slot's outer edge. When that
 * leaves a text row below [MIN_TEXT_SCALE] (and below the uniform scale), all text rows are capped a little smaller
 * first, which pulls the stack together and widens the chord at the rim; only when that is not enough does the first
 * row shrink too, in steps, down to the uniform [circleFitScale] as the last resort. Every row stays inside its slot
 * and inside the circle.
 */
fun circleFitRows(widths: List<Float>, heights: List<Float>, diameter: Float): CircleFit {
    val uniform = circleFitScale(widths, heights, diameter)
    if (uniform >= 1f || widths.size < 2) return uniformFit(heights, uniform)
    val floor = minOf(uniform, MIN_TEXT_SCALE)
    for (firstCap in caps(from = 1f, downTo = uniform)) {
        for (textCap in caps(from = 1f, downTo = floor)) {
            val fit = perRowFit(widths, heights, diameter, firstCap, textCap)
            // Accept only when the number really kept this cap: perRowFit also caps it by its own chord, and a shrunk
            // number must not win over a smaller text cap that keeps it whole (I-C).
            if (fit.scales.first() >= firstCap - FIT_EPSILON && fit.scales.drop(1).all { it >= floor }) return fit
        }
    }
    return uniformFit(heights, uniform)
}

private fun caps(from: Float, downTo: Float): List<Float> =
    generateSequence(from) { it - CAP_STEP }.takeWhile { it > downTo }.toList() + downTo

private fun uniformFit(heights: List<Float>, scale: Float) = CircleFit(List(heights.size) { scale }, heights.map { it * scale })

/**
 * Each pass shrinks every row to the chord at its current slot's outer edge, then tightens the slots to the new
 * heights. The returned scales were checked against the returned slots, so the result fits even if it stops early.
 */
private fun perRowFit(widths: List<Float>, heights: List<Float>, diameter: Float, firstCap: Float, textCap: Float): CircleFit {
    var scales = List(heights.size) { if (it == 0) firstCap else textCap }
    var fit = CircleFit(scales, heights)
    repeat(FIT_PASSES) {
        val slots = heights.zip(scales) { h, s -> h * s }
        val next = chordScales(widths, slots, diameter).zip(scales) { c, s -> minOf(c, s) }
        fit = CircleFit(next, slots)
        if (next == scales) return fit
        scales = next
    }
    return fit
}

/** Largest scale at which each row's width fits the circle's chord at its slot's edge farthest from the centre. */
private fun chordScales(widths: List<Float>, slots: List<Float>, diameter: Float): List<Float> {
    val r = diameter / 2f
    var top = -slots.sum() / 2f
    return widths.zip(slots) { w, h ->
        val far = maxOf(abs(top), abs(top + h))
        top += h
        val chord = 2f * sqrt(maxOf(0f, r * r - far * far))
        if (w > 0f) chord / w else if (far < r) 1f else 0f
    }
}

/**
 * Column for the ring centre: each child is measured at its natural size (keep lines single-line) and fitted with
 * [circleFitRows] into the circle inscribed in the incoming bounds: the first row keeps its size whenever it fits,
 * the lines below shrink on their own. Nothing wraps, nothing is clipped, nothing touches the beads.
 */
@Composable
fun CircleFitColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val rows = measurables.map { it.measure(Constraints()) }
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else rows.maxOfOrNull { it.width } ?: 0
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else rows.sumOf { it.height }
        val fit = circleFitRows(rows.map { it.width.toFloat() }, rows.map { it.height.toFloat() }, minOf(w, h).toFloat())
        layout(w, h) {
            var top = h / 2f - fit.slots.sum() / 2f
            rows.forEachIndexed { i, row ->
                val y = top + fit.slots[i] / 2f - row.height / 2f
                row.placeWithLayer((w - row.width) / 2, y.roundToInt()) {
                    scaleX = fit.scales[i]
                    scaleY = fit.scales[i]
                }
                top += fit.slots[i]
            }
        }
    }
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
        Canvas(Modifier.size(ringSize).semantics { ringTone = tone }) {
            drawRing(scheme.ringColors(tone), beads.coerceAtLeast(1), fill, safety = tone == RingTone.SAFETY)
        }
        Box(Modifier.size(ringInnerDiameter(ringSize)), contentAlignment = Alignment.Center) { center(ringSize) }
        if (sticker != null) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = ringSize * 0.06f, y = -ringSize * 0.05f).rotate(8f)) {
                sticker(ringSize * 0.3f)
            }
        }
    }
}
