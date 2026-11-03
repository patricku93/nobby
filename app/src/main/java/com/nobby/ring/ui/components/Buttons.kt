package com.nobby.ring.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbySpace

enum class ButtonTone { PRIMARY, TONAL, OUTLINED, TEXT, DESTRUCTIVE }

/**
 * The 3 dp darker bottom edge of a filled "sticker" button (bundle `.btn` inset shadow), drawn over the bottom of
 * the pill after its content so it sits on the fill like the CSS inset shadow.
 */
fun Modifier.stickerEdge(color: Color, shape: Shape = CircleShape, edge: Dp = 3.dp): Modifier = drawWithContent {
    drawContent()
    val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
    clipPath(clip) {
        drawRect(color, topLeft = Offset(0f, size.height - edge.toPx()), size = Size(size.width, edge.toPx()))
    }
}

private fun Modifier.edgeIf(enabled: Boolean, color: Color): Modifier = if (enabled) stickerEdge(color) else this

/** Pill button, min height 52 dp. Use one PRIMARY per screen; TEXT/OUTLINED take [contentColor] on coloured cards. */
@Composable
fun NobbyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.PRIMARY,
    icon: ImageVector? = null,
    contentColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val sized = modifier.heightIn(min = NobbySpace.button)
    val padding = PaddingValues(horizontal = if (tone == ButtonTone.TEXT) 12.dp else 24.dp)
    val quiet = if (contentColor.isSpecified) contentColor else c.primary
    val body: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
    when (tone) {
        ButtonTone.PRIMARY -> Button(
            onClick = onClick, modifier = sized.edgeIf(enabled, Color.Black.copy(alpha = 0.16f)), enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = c.onPrimary),
            contentPadding = padding, content = body,
        )
        ButtonTone.TONAL -> Button(
            onClick = onClick, modifier = sized.edgeIf(enabled, c.onPrimaryContainer.copy(alpha = 0.10f)), enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer),
            contentPadding = padding, content = body,
        )
        ButtonTone.DESTRUCTIVE -> Button(
            onClick = onClick, modifier = sized.edgeIf(enabled, Color.Black.copy(alpha = 0.18f)), enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = c.error, contentColor = c.onError),
            contentPadding = padding, content = body,
        )
        ButtonTone.OUTLINED -> OutlinedButton(
            onClick = onClick, modifier = sized, enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = quiet),
            border = BorderStroke(1.5.dp, if (enabled) c.outline else c.onSurface.copy(alpha = 0.12f)),
            contentPadding = padding, content = body,
        )
        ButtonTone.TEXT -> TextButton(
            onClick = onClick, modifier = sized, enabled = enabled,
            colors = ButtonDefaults.textButtonColors(contentColor = quiet),
            contentPadding = padding, content = body,
        )
    }
}
