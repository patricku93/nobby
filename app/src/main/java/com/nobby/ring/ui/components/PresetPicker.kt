package com.nobby.ring.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.roles

/**
 * The five colour presets (bundle `.preset-pick`): split swatch primary / primaryContainer with an accent dot, in the
 * current mode; selection = cocoa ring + check badge. Radio semantics, test tags `preset_<NAME>`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetPicker(selected: Palette, onSelect: (Palette) -> Unit, modifier: Modifier = Modifier) {
    val dark = NobbyTheme.extra.dark
    val c = MaterialTheme.colorScheme
    val gap = if (dark) c.surfaceContainer else c.surfaceContainerLowest
    // Wraps to a second line when the five names do not fit (DE "Blaubeere" at font scale 1.3) instead of truncating.
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Palette.entries.forEach { p ->
            val on = p == selected
            val roles = p.roles(dark)
            Column(
                Modifier
                    .widthIn(min = 60.dp)
                    .testTag("preset_${p.name}")
                    .selectable(selected = on, onClick = { onSelect(p) }, role = Role.RadioButton)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(46.dp)) {
                        val r = size.minDimension / 2f
                        if (on) {
                            drawCircle(gap, r + 3.dp.toPx())
                            drawCircle(c.inverseSurface, r + 4.25.dp.toPx(), style = Stroke(2.5.dp.toPx()))
                        }
                        drawCircle(roles.primaryContainer, r)
                        drawArc(roles.primary, startAngle = 90f, sweepAngle = 180f, useCenter = true)
                        drawCircle(c.outline.copy(alpha = 0.35f), r, style = Stroke(1.dp.toPx()))
                        val dotR = 7.5.dp.toPx()
                        val dot = Offset(size.width - dotR + 1.dp.toPx(), size.height - dotR + 1.dp.toPx())
                        drawCircle(gap, dotR + 2.5.dp.toPx(), dot)
                        drawCircle(roles.accent, dotR, dot)
                    }
                    if (on) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(c.inverseSurface), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(16.dp), tint = c.inverseOnSurface)
                        }
                    }
                }
                Text(
                    p.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.onSurface else c.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
