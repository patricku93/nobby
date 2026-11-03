package com.nobby.ring.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobby.ring.R

@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StepButton(Icons.Rounded.Remove, stringResource(R.string.stepper_minus), value > range.first) { onValueChange((value - 1).coerceIn(range)) }
        Text(
            "$value",
            Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        )
        StepButton(Icons.Rounded.Add, stringResource(R.string.stepper_plus), value < range.last) { onValueChange((value + 1).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer),
    ) { Icon(icon, contentDescription = description) }
}
