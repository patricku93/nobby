package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ChipTone { WEAR, PAUSE, OVERDUE }

/** Non-interactive 34 dp phase chip with a dot; the tone carries the meaning (wear / pause / overdue). */
@Composable
fun PhaseChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val (bg, fg, dot) = when (tone) {
        ChipTone.WEAR -> Triple(c.primaryContainer, c.onPrimaryContainer, c.primary)
        ChipTone.PAUSE -> Triple(c.secondaryContainer, c.onSecondaryContainer, c.secondary)
        ChipTone.OVERDUE -> Triple(c.errorContainer, c.onErrorContainer, c.error)
    }
    Row(
        modifier.heightIn(min = 34.dp).clip(CircleShape).background(bg).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Selectable pill: cocoa ink (inverseSurface) when selected, hairline outline otherwise. 48 dp touch target. */
@Composable
fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val fg = if (selected) c.inverseOnSurface else c.onSurface
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(if (selected) c.inverseSurface else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.outlineVariant, CircleShape))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(18.dp), tint = fg)
        Text(label, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
    }
}

/** A row of [ChoiceChip]s with single selection, e.g. Light / Dark / Auto. */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> ChoiceChip(option == selected, { onSelect(option) }, label(option)) }
    }
}

/** Material segmented buttons with the v2 selection colour (inverseSurface). */
@Composable
fun nobbySegmentedColors(): SegmentedButtonColors {
    val c = MaterialTheme.colorScheme
    return SegmentedButtonDefaults.colors(
        activeContainerColor = c.inverseSurface,
        activeContentColor = c.inverseOnSurface,
        activeBorderColor = c.inverseSurface,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = c.onSurface,
        inactiveBorderColor = c.outlineVariant,
    )
}
