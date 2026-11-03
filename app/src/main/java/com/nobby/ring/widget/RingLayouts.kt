package com.nobby.ring.widget

import android.content.Context
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

private enum class RingShape { SMALL, SQUARE, WIDE }

private fun shapeOf(size: DpSize): RingShape = when {
    size.height < RingWidget.MEDIUM.height -> RingShape.SMALL
    size.width < RingWidget.WIDE.width -> RingShape.SQUARE
    else -> RingShape.WIDE
}

private fun content(size: DpSize) = DpSize(size.width - RING_PADDING * 2, size.height - RING_PADDING * 2)

/** Diameter of the ring itself (without the sticker room) for this widget size. */
private fun ringDp(size: DpSize): Dp {
    val c = content(size)
    return when (shapeOf(size)) {
        RingShape.SMALL -> minOf(c.height, c.width / 2)
        RingShape.SQUARE -> minOf(c.width, c.height) / STICKER_ROOM
        RingShape.WIDE -> minOf(c.height, c.width / 2) / STICKER_ROOM
    }.coerceAtLeast(1.dp)
}

/** Ring pictures for this widget, or null for Classic (style, Multi mode or a failed render). */
internal fun ringImages(data: WidgetData, config: WidgetConfig, size: DpSize, context: Context): List<RingImage>? {
    if (config.mode != WidgetMode.SINGLE || config.style != WidgetStyle.RING) return null
    val ring = ringDp(size).value
    val density = context.resources.displayMetrics.density
    val edge = RingBitmaps.edgePx(ring, density, RingBitmaps.layersFor(data.tone).size)
    val spec = data.ringSpec(edge, edge / ring, withLabel = shapeOf(size) != RingShape.SMALL)
    return RingBitmaps.renderOrNull(spec) { RingBitmaps.fredoka(context) }
}

/** Ring style: 2x1 ring + label, square ring with sticker, wide ring + next event + Done (when due). */
@Composable
internal fun RingLayout(d: WidgetData, images: List<RingImage>, p: WidgetPalette, labels: WidgetLabels, size: DpSize) {
    val ring = ringDp(size)
    when (shapeOf(size)) {
        RingShape.SMALL -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            RingStack(images, d, p, ring)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(d.head().label, style = text(p.of { if (d.urgent()) it.err else it.fg }, 14, FontWeight.Bold), maxLines = 2)
                if (!d.urgent()) Text(d.dayLabel, style = text(p.of { it.sub }, 12), maxLines = 1)
            }
        }
        RingShape.SQUARE -> StickerRing(images, d, p, ring)
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

/** The alpha-mask layers of [RingBitmaps], each tinted with its day/night colour; together they are Home's ring. */
@Composable
private fun RingStack(images: List<RingImage>, d: WidgetData, p: WidgetPalette, ring: Dp) {
    Box(GlanceModifier.size(ring)) {
        images.forEach { img ->
            Image(
                ImageProvider(img.bitmap), null, GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(p.of { it.ringTint(d.tone, img.layer) }),
            )
        }
    }
}
