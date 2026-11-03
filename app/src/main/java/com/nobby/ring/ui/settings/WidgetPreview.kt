package com.nobby.ring.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetStyle
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.components.drawRing
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.ring.RingLabel
import com.nobby.ring.ui.ring.RingModel
import com.nobby.ring.ui.theme.NobbyType
import com.nobby.ring.ui.theme.isDark
import com.nobby.ring.ui.theme.roles
import com.nobby.ring.widget.WidgetColors
import com.nobby.ring.widget.widgetColors

/** Shown when there is no schedule yet: wear day 12 of 21. */
private val SAMPLE = RingModel(RingTone.WEAR, MascotMood.HAPPY, 21, 11, 10, RingLabel.UNTIL_REMOVE, null, false)

/**
 * Live preview of the widget on a sample wallpaper strip, a light and a dark band, so the background opacity can be
 * judged against both. Uses the widget's own colours ([widgetColors]) and Home's ring painter.
 */
@Composable
fun WidgetPreview(config: WidgetConfig, theme: ThemeSpec, model: RingModel?, modifier: Modifier = Modifier) {
    val colors = theme.palette.widgetColors(theme.isDark())
    val light = theme.palette.roles(dark = false)
    val dark = theme.palette.roles(dark = true)
    val bands = listOf(
        Brush.horizontalGradient(listOf(light.primaryContainer, light.tertiaryContainer)),
        Brush.horizontalGradient(listOf(dark.surfaceContainerLowest, dark.primaryContainer)),
    )
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        bands.forEach { band ->
            Box(Modifier.fillMaxWidth().background(band).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                MiniWidget(config, colors, model ?: SAMPLE)
            }
        }
    }
}

@Composable
private fun MiniWidget(config: WidgetConfig, c: WidgetColors, m: RingModel) {
    val urgent = m.tone == RingTone.OVERDUE || m.tone == RingTone.SAFETY
    val number = if (m.label == RingLabel.TODAY) stringResource(R.string.ring_today) else m.number?.toString() ?: "–"
    val ring = config.mode == WidgetMode.SINGLE && config.style == WidgetStyle.RING
    Row(
        Modifier.widthIn(max = 240.dp).fillMaxWidth(0.8f).height(84.dp).clip(RoundedCornerShape(24.dp))
            .background(c.background(config.backgroundOpacity)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (ring) {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(72.dp)) { drawRing(c.ring(m.tone), m.beads.coerceAtLeast(1), m.done.toFloat(), m.tone == RingTone.SAFETY) }
                Text(number, style = NobbyType.countdown.copy(fontSize = if (number.length > 3) 12.sp else 24.sp, lineHeight = 26.sp), color = if (urgent) c.err else c.fg, maxLines = 1)
            }
        } else {
            Text(number, color = if (urgent) c.err else c.fg, fontSize = if (number.length > 3) 22.sp else 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(m.previewLabel(), color = if (urgent) c.err else c.sub, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}

@Composable
private fun RingModel.previewLabel(): String {
    val n = number ?: 0
    return when (label) {
        RingLabel.UNTIL_REMOVE -> pluralStringResource(R.plurals.ring_until_remove, n)
        RingLabel.UNTIL_INSERT -> pluralStringResource(R.plurals.ring_until_insert, n)
        RingLabel.UNTIL_SWAP -> pluralStringResource(R.plurals.ring_until_swap, n)
        RingLabel.OVERDUE_DAYS -> pluralStringResource(R.plurals.ring_overdue_days, n)
        RingLabel.OVERDUE_HOURS -> pluralStringResource(R.plurals.ring_overdue_hours, n)
        RingLabel.NONE -> stringResource(R.string.widget_none)
        RingLabel.TODAY -> stringResource(
            when (event?.kind) {
                EventKind.REMOVE -> R.string.event_remove
                EventKind.INSERT -> R.string.event_insert
                EventKind.SWAP -> R.string.event_swap
                null -> R.string.widget_none
            },
        )
    }
}
