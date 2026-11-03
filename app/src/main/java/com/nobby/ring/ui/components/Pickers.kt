package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius

/** Tappable field: small label above the value, leading icon. Replaces the v1 "$label: $value" buttons. */
@Composable
fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(NobbyRadius.md))
            .background(c.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        }
    }
}
