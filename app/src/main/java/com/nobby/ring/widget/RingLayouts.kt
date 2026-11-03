package com.nobby.ring.widget

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetStyle
import com.nobby.ring.ui.components.formatDateTimeCompact

internal val RING_PADDING = 8.dp

/** The sticker box is this much larger than the ring, so NoBby can sit on the rim top-right as on Home. */
private const val STICKER_ROOM = 1.08f
private const val STICKER_SHARE = 0.3f

internal enum class RingShape { SMALL, SQUARE, WIDE }

/** Room under the square ring for the Done pill (36 dp) and its gap. */
internal val DONE_ROOM = 42.dp
private val DONE_GAP = 6.dp

/** Ring-style geometry of one widget size: the shape, the ring diameter (without the sticker room) and the Done pill. */
internal data class RingLayoutModel(val shape: RingShape, val ring: Dp, val donePill: Boolean) {
    /** 2x1 shows only the number in the ring; its label sits beside it. */
    val withLabel: Boolean get() = shape != RingShape.SMALL
}

private fun shapeOf(size: DpSize): RingShape = when {
    size.height < RingWidget.MEDIUM.height -> RingShape.SMALL
    size.width < RingWidget.WIDE.width -> RingShape.SQUARE
    else -> RingShape.WIDE
}

private fun content(size: DpSize) = DpSize(size.width - RING_PADDING * 2, size.height - RING_PADDING * 2)

/** Done shows (under the ring when square, in the event column when wide) only while an event is [due]; 2x1 never. */
internal fun ringLayoutModel(size: DpSize, due: Boolean): RingLayoutModel {
    val shape = shapeOf(size)
    val c = content(size)
    val pill = due && shape != RingShape.SMALL
    val ring = when (shape) {
        RingShape.SMALL -> minOf(c.height, c.width / 2)
        RingShape.SQUARE -> minOf(c.width, c.height - if (pill) DONE_ROOM else 0.dp) / STICKER_ROOM
        RingShape.WIDE -> minOf(c.height, c.width / 2) / STICKER_ROOM
    }.coerceAtLeast(1.dp)
    return RingLayoutModel(shape, ring, pill)
}

/**
 * Ring pictures for this widget, or null for Classic (style, Multi mode, a failed render or a failed Ring update).
 * The picture depends on the state and the shape's label only, never on [size] (see [RingBudget]).
 */
internal fun ringImages(data: WidgetData, config: WidgetConfig, size: DpSize, font: () -> Typeface): List<RingImage>? {
    if (config.mode != WidgetMode.SINGLE || config.style != WidgetStyle.RING || RingFallback.active) return null
    val spec = data.ringSpec(withLabel = ringLayoutModel(size, due = false).withLabel)
    return RingBitmaps.renderOrNull(spec, font = font)
}

/** Ring style: 2x1 ring + label, square ring with sticker, wide ring + next event + Done (when due). */
@Composable
internal fun RingLayout(d: WidgetData, images: List<RingImage>, p: WidgetPalette, labels: WidgetLabels, size: DpSize) {
    val m = ringLayoutModel(size, due = d.dueEventId != null)
    val ring = m.ring
    when (m.shape) {
        RingShape.SMALL -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            RingStack(images, d, p, ring)
            Spacer(GlanceModifier.width(10.dp))
            // 34 dp of height at the 50 dp minimum: one line each, 13 sp like Classic 2x1.
            Column(GlanceModifier.defaultWeight()) {
                Text(d.head().label, style = text(p.of { if (d.urgent()) it.err else it.fg }, 13, FontWeight.Bold), maxLines = 1)
                if (!d.urgent()) Text(d.dayLabel, style = text(p.of { it.sub }, 12), maxLines = 1)
            }
        }
        RingShape.SQUARE -> Column(
            GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StickerRing(images, d, p, ring)
            if (m.donePill) {
                Spacer(GlanceModifier.height(DONE_GAP))
                DonePill(labels.done, d.urgent(), p)
            }
        }
        RingShape.WIDE -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            StickerRing(images, d, p, ring)
            Spacer(GlanceModifier.width(14.dp))
            NextColumn(d, p, labels)
        }
    }
}

@Composable
private fun RowScope.NextColumn(d: WidgetData, p: WidgetPalette, labels: WidgetLabels) {
    Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        // While overdue the day label is the unit, which the ring already shows.
        if (!d.urgent()) Text(d.dayLabel, style = text(p.of { it.sub }, 13), maxLines = 1)
        val next = d.rows.firstOrNull()
        if (next != null) Text(next.label, style = text(p.of { if (d.urgent()) it.err else it.fg }, 16, FontWeight.Bold), maxLines = 2)
        d.nextAt?.let { Text(formatDateTimeCompact(it), style = text(p.of { it.sub }, 13), maxLines = 1) }
        if (d.dueEventId != null) {
            Spacer(GlanceModifier.height(8.dp))
            DonePill(labels.done, d.urgent(), p)
        }
    }
}

/** The ring with NoBby on its rim, top-right (the only mascot of the widget). */
@Composable
private fun StickerRing(images: List<RingImage>, d: WidgetData, p: WidgetPalette, ring: Dp) {
    Box(GlanceModifier.size(ring * STICKER_ROOM)) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) { RingStack(images, d, p, ring) }
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) { WidgetFace(d.mood, p, ring * STICKER_SHARE) }
    }
}

/**
 * The alpha-mask layers of [RingBitmaps], each tinted with its day/night colour; together they are Home's ring.
 * The countdown exists only as pixels, so the first layer carries it as TalkBack text ([WidgetData.description]).
 */
@Composable
private fun RingStack(images: List<RingImage>, d: WidgetData, p: WidgetPalette, ring: Dp) {
    Box(GlanceModifier.size(ring)) {
        images.forEachIndexed { i, img ->
            Image(
                ImageProvider(img.bitmap), if (i == 0) d.description else null, GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(p.of { it.ringTint(d.tone, img.layer) }),
            )
        }
    }
}
