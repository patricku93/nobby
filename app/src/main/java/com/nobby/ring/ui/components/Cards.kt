package com.nobby.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbySpace
import com.nobby.ring.ui.theme.NobbyTheme

enum class CardTone { PLAIN, TONAL, DANGER, FLAT }

/**
 * 24 dp card, 20 dp padding. PLAIN = white on paper with a hairline (dark: surfaceContainer + hairline); colour-filled
 * tones are for state only: TONAL = coming up / due, DANGER = overdue, FLAT = quiet grouping.
 */
@Composable
fun NobbyCard(modifier: Modifier = Modifier, tone: CardTone = CardTone.PLAIN, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    val (bg, fg) = when (tone) {
        CardTone.PLAIN -> (if (dark) c.surfaceContainer else c.surfaceContainerLowest) to c.onSurface
        CardTone.TONAL -> c.primaryContainer to c.onPrimaryContainer
        CardTone.DANGER -> c.errorContainer to c.onErrorContainer
        CardTone.FLAT -> c.surfaceContainer to c.onSurface
    }
    val shape = RoundedCornerShape(NobbyRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .then(if (tone == CardTone.PLAIN) Modifier.border(1.dp, c.outlineVariant, shape) else Modifier)
            .padding(NobbySpace.card),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { this@Column.content() }
    }
}

enum class TileTone { WEAR, PAUSE, NEUTRAL, ERROR }

/** Rounded icon tile (16 dp radius) used on cards and rows. Decorative: the row text carries the meaning. */
@Composable
fun IconTile(icon: ImageVector, tone: TileTone, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        TileTone.WEAR -> c.primaryContainer to c.onPrimaryContainer
        TileTone.PAUSE -> c.secondaryContainer to c.onSecondaryContainer
        TileTone.NEUTRAL -> c.surfaceContainerHigh to c.onSurface
        TileTone.ERROR -> c.error to c.onError
    }
    Box(modifier.size(size).clip(RoundedCornerShape(NobbyRadius.md)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = fg)
    }
}

/** Plain card with a strip of washi tape (accent, 85 %, rotated -4°) on its top edge. */
@Composable
fun NoteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tape = NobbyTheme.extra.sticker
    Box(modifier.fillMaxWidth().padding(top = 10.dp)) {
        NobbyCard(content = content)
        Box(
            Modifier
                .offset(x = 28.dp, y = (-9).dp)
                .size(width = 64.dp, height = 20.dp)
                .rotate(-4f)
                .clip(RoundedCornerShape(4.dp))
                .background(tape.copy(alpha = 0.85f)),
        )
    }
}
