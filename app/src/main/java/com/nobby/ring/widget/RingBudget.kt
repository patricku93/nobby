package com.nobby.ring.widget

import com.nobby.ring.ui.components.RingTone
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Byte budget of the widget ring pictures. One widget update sends a RemoteViews per launcher-reported size (phones
 * two, foldables and tablets four or more) in a single Binder transaction (1 MB for the whole process). The picture
 * therefore never depends on the widget size: every size of one update gets the same [RingSpec], so the cache hands
 * out the same Bitmap instances and RemoteViews' shared bitmap cache sends each of them once; the ImageView scales.
 * Per update there are at most [MAX_VARIANTS] specs (a ring with label and one with only the number, 2x1), so all
 * ring pictures of one update stay within [TOTAL_BYTES] whatever the number of sizes.
 */
object RingBudget {
    const val TOTAL_BYTES = 400 * 1024
    const val MAX_VARIANTS = 2

    /** Upper bound for all layers of one ring (ALPHA_8, one byte per pixel). */
    const val MAX_BYTES = TOTAL_BYTES / MAX_VARIANTS

    /** Home's ring diameter: the picture keeps Home's proportions (insets in dp of a 264 dp ring) at every size. */
    const val HOME_RING_DP = 264f

    fun layersFor(tone: RingTone): List<RingLayer> =
        RingLayer.entries.filter { it != RingLayer.SAFETY || tone == RingTone.SAFETY }

    /** Largest edge (a multiple of 4) at which [layers] masks fit [MAX_BYTES]. */
    fun edgePx(layers: Int): Int = (floor(sqrt(MAX_BYTES / layers.coerceAtLeast(1).toDouble())).toInt() / 4) * 4

    fun bytes(spec: RingSpec): Long = spec.edgePx.toLong() * spec.edgePx * layersFor(spec.tone).size
}
