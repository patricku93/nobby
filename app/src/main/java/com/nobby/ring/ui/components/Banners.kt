package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbyTheme

/** Compact, non-alarming hint (butter tertiaryContainer; dark: surfaceContainerHigh + tertiary hairline). */
@Composable
fun PermissionBanner(text: String, icon: ImageVector, actionLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    val shape = RoundedCornerShape(NobbyRadius.md)
    val fg = if (dark) c.onSurface else c.onTertiaryContainer
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) c.surfaceContainerHigh else c.tertiaryContainer)
            .then(if (dark) Modifier.border(1.dp, c.tertiary.copy(alpha = 0.45f), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = c.tertiary)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            actionLabel,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
            color = if (dark) c.tertiary else c.onTertiaryContainer,
        )
    }
}
