package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbyTheme

/** A titled group of rows in one 24 dp list card (bundle `.list`). */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(NobbyRadius.lg)
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (NobbyTheme.extra.dark) c.surfaceContainer else c.surfaceContainerLowest)
                .border(1.dp, c.outlineVariant, shape),
            content = content,
        )
    }
}

/** 68 dp row: neutral icon tile, title, optional summary, chevron when clickable (or a custom [trailing]). */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, TileTone.NEUTRAL, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.onSurfaceVariant)
        }
    }
}

/** Hairline between rows, inset past the icon tile. */
@Composable
fun SettingsDivider() = HorizontalDivider(
    Modifier.padding(start = 70.dp, end = 16.dp),
    thickness = 1.dp,
    color = MaterialTheme.colorScheme.outlineVariant,
)
