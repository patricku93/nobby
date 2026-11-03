package com.nobby.ring.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import com.nobby.ring.R
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetStyle
import com.nobby.ring.ui.components.RING_NUMBER_KEEP
import com.nobby.ring.ui.components.RingColors
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.circleFitRows
import com.nobby.ring.ui.components.drawRing
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One tint group of the widget bracelet. Each layer is an alpha mask drawn by Home's [drawRing] with only its own
 * colour role switched on; the widget stacks the masks as Images tinted with day/night colours, so the ring follows
 * the system dark mode exactly like the rest of the widget and one render serves every preset.
 */
enum class RingLayer { SAFETY, TRACK, HALO, BEAD, FUTURE, TEXT }

/** What one ring picture shows. [density] is pixels per dp of the picture, so dp insets keep Home's proportions. */
data class RingSpec(
    val tone: RingTone,
    val beads: Int,
    val done: Int,
    val number: String,
    val label: String?,
    val edgePx: Int,
    val density: Float,
) {
    val urgent: Boolean get() = tone == RingTone.OVERDUE || tone == RingTone.SAFETY
}

data class RingImage(val layer: RingLayer, val bitmap: Bitmap)

/**
 * Ring picture parameters for this widget state; [withLabel] false shows only the number (2x1). The edge is the
 * [RingBudget] cap and never the widget size, so every reported size of one update shares the same pictures.
 */
fun WidgetData.ringSpec(withLabel: Boolean): RingSpec {
    val edge = RingBudget.edgePx(RingBudget.layersFor(tone).size)
    val label = if (withLabel) head().label.ifBlank { null } else null
    return RingSpec(tone, beads, done, head().number, label, edge, edge / RingBudget.HOME_RING_DP)
}

/** The Ring layout needs Single mode, the Ring style and a rendered picture; anything else shows Classic. */
fun useRingLayout(config: WidgetConfig, images: List<RingImage>?): Boolean =
    config.mode == WidgetMode.SINGLE && config.style == WidgetStyle.RING && !images.isNullOrEmpty()

/** Tint of [layer] for a ring in [tone]: Home's ring colours, the number in onSurface (or the bead red when urgent). */
fun WidgetColors.ringTint(tone: RingTone, layer: RingLayer): Color = ring(tone).let {
    when (layer) {
        RingLayer.SAFETY -> it.safetyDisc
        RingLayer.TRACK -> it.track
        RingLayer.HALO -> it.halo
        RingLayer.BEAD -> it.bead
        RingLayer.FUTURE -> it.future
        RingLayer.TEXT -> fg
    }
}

object RingBitmaps {
    private const val NUMBER_SHARE = 0.36f // Home: 96 sp number in a 264 dp ring
    private const val WORD_SHARE = 0.17f // Home: 45 sp "Heute"
    private const val LABEL_SHARE = 0.13f

    private val cache = LruCache<RingSpec, List<RingImage>>(6)
    private val failureLogged = AtomicBoolean(false)
    @Volatile private var fredoka: Typeface? = null

    fun clearCache() = cache.evictAll()

    fun fredoka(context: Context): Typeface =
        fredoka ?: requireNotNull(ResourcesCompat.getFont(context, R.font.fredoka_variable)) { "Fredoka missing" }.also { fredoka = it }

    /**
     * Cached [render]. A spec over [RingBudget.MAX_BYTES] is refused before drawing; any failure (font, out of memory,
     * drawing) is logged once. Both yield null, so the widget shows Classic.
     */
    fun renderOrNull(
        spec: RingSpec,
        render: (RingSpec, Typeface) -> List<RingImage> = ::render,
        font: () -> Typeface,
    ): List<RingImage>? {
        cache.get(spec)?.let { return it }
        if (RingBudget.bytes(spec) > RingBudget.MAX_BYTES) {
            logOnce(IllegalArgumentException("ring picture ${spec.edgePx} px over the budget"))
            return null
        }
        return try {
            render(spec, font()).also { cache.put(spec, it) }
        } catch (e: Exception) {
            logOnce(e)
            null
        } catch (e: OutOfMemoryError) {
            logOnce(e)
            null
        }
    }

    private fun logOnce(e: Throwable) {
        if (failureLogged.compareAndSet(false, true)) Log.e("NoBby", "widget ring render failed, showing classic", e)
    }

    fun render(spec: RingSpec, typeface: Typeface): List<RingImage> {
        val text = RingText(spec, typeface)
        return RingBudget.layersFor(spec.tone).map { RingImage(it, drawLayer(spec, it, text)) }
    }

    private fun drawLayer(spec: RingSpec, layer: RingLayer, text: RingText): Bitmap {
        val px = spec.edgePx
        val image = ImageBitmap(px, px, ImageBitmapConfig.Alpha8)
        CanvasDrawScope().draw(Density(spec.density), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
            drawRing(mask(layer), spec.beads.coerceAtLeast(1), spec.done.toFloat(), safety = spec.tone == RingTone.SAFETY)
            text.draw(drawContext.canvas.nativeCanvas, layer)
        }
        return image.asAndroidBitmap()
    }

    private fun mask(layer: RingLayer): RingColors {
        fun on(l: RingLayer) = if (l == layer) Color.White else Color.Transparent
        return RingColors(on(RingLayer.SAFETY), on(RingLayer.TRACK), on(RingLayer.HALO), on(RingLayer.BEAD), on(RingLayer.FUTURE))
    }

    /** Number (and label) in Fredoka, fitted into the free disc inside the beads with Home's [circleFitRows]. */
    private class RingText(private val spec: RingSpec, typeface: Typeface) {
        private class Row(val text: String, val paint: Paint, val height: Float, val layer: RingLayer)

        private val rows: List<Row>
        private val scales: List<Float>
        private val slots: List<Float>

        init {
            val px = spec.edgePx.toFloat()
            val digits = spec.number.all { it.isDigit() }
            val number = paint(typeface, px * if (digits) NUMBER_SHARE else WORD_SHARE, weight = 600, tnum = digits)
            val numberHeight = number.lineHeight() * if (digits) RING_NUMBER_KEEP else 1f
            rows = listOfNotNull(
                Row(spec.number, number, numberHeight, if (spec.urgent) RingLayer.BEAD else RingLayer.TEXT),
                spec.label?.let { label ->
                    val p = paint(typeface, px * LABEL_SHARE, weight = 500, tnum = false)
                    Row(label, p, p.lineHeight(), RingLayer.TEXT)
                },
            )
            // Home: ringInnerDiameter(ring) = ring x 0.81 - 20 dp.
            val inner = (px * 0.81f - 20f * spec.density).coerceAtLeast(1f)
            val fit = circleFitRows(rows.map { it.paint.measureText(it.text) }, rows.map { it.height }, inner)
            scales = fit.scales
            slots = fit.slots
        }

        fun draw(canvas: android.graphics.Canvas, layer: RingLayer) {
            val c = spec.edgePx / 2f
            var top = c - slots.sum() / 2f
            rows.forEachIndexed { i, row ->
                val mid = top + slots[i] / 2f
                top += slots[i]
                if (row.layer != layer) return@forEachIndexed
                val fm = row.paint.fontMetrics
                canvas.save()
                canvas.scale(scales[i], scales[i], c, mid)
                canvas.drawText(row.text, c, mid - (fm.ascent + fm.descent) / 2f, row.paint)
                canvas.restore()
            }
        }

        private fun Paint.lineHeight(): Float = fontMetrics.let { it.descent - it.ascent }

        private fun paint(typeface: Typeface, size: Float, weight: Int, tnum: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            fontVariationSettings = "'wght' $weight"
            if (tnum) fontFeatureSettings = "tnum"
        }
    }
}
