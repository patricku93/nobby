package com.nobby.ring.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.ui.components.IconTile
import com.nobby.ring.ui.components.TileTone
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.components.label
import com.nobby.ring.ui.mascot.DoneBurst
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.Nobby
import com.nobby.ring.ui.theme.NobbyMotion
import com.nobby.ring.ui.theme.NobbyRadius
import com.nobby.ring.ui.theme.NobbyTheme
import com.nobby.ring.ui.theme.reducedMotion
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt

const val DONE_SHEET_TAG = "done_sheet"
const val AUTO_DISMISS_MS = 4_000L

private fun EventKind.doneTitle(): Int = when (this) {
    EventKind.REMOVE -> R.string.done_title_remove
    EventKind.INSERT -> R.string.done_title_insert
    EventKind.SWAP -> R.string.done_title_swap
}

/**
 * Done celebration (bundle screens/done-sheet.html, without the "Rückgängig" button by owner decision): scrim + bottom
 * sheet with the yay mascot in the burst. Closes after 4 s, on tap anywhere or on a downward swipe.
 */
@Composable
fun DoneSheet(celebration: Celebration, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = NobbyTheme.extra.dark
    val reduced = reducedMotion()
    val dismiss by rememberUpdatedState(onDismiss)
    val countdown = remember(celebration) { Animatable(1f) }
    val enter = remember(celebration) { Animatable(0f) }
    val pop = remember(celebration) { Animatable(if (reduced) 1f else 0.6f) }
    var dragY by remember(celebration) { mutableFloatStateOf(0f) }
    val dismissPx = with(LocalDensity.current) { 80.dp.toPx() }

    LaunchedEffect(celebration) {
        enter.animateTo(1f, tween(if (reduced) NobbyMotion.SHORT else NobbyMotion.LONG, easing = NobbyMotion.Decelerate))
    }
    LaunchedEffect(celebration) { if (!reduced) pop.animateTo(1f, NobbyMotion.squishSpring()) }
    LaunchedEffect(celebration) {
        countdown.animateTo(0f, tween(AUTO_DISMISS_MS.toInt(), easing = LinearEasing))
        dismiss()
    }

    Box(modifier.fillMaxSize().testTag(DONE_SHEET_TAG)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(c.scrim.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { dismiss() }),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, dragY.roundToInt()) }
                .graphicsLayer {
                    alpha = if (reduced) enter.value else 1f
                    translationY = if (reduced) 0f else (1f - enter.value) * size.height
                }
                .clip(RoundedCornerShape(topStart = NobbyRadius.xl, topEnd = NobbyRadius.xl))
                .background(if (dark) c.surfaceContainerHigh else c.surfaceContainerLow)
                .pointerInput(celebration) {
                    detectVerticalDragGestures(onDragEnd = { if (dragY > dismissPx) dismiss() else dragY = 0f }) { _, dy ->
                        dragY = (dragY + dy).coerceAtLeast(0f)
                    }
                }
                .clickable(onClick = { dismiss() })
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(if (dark) c.outline else c.outlineVariant))
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                DoneBurst(Modifier.fillMaxWidth().widthIn(max = 320.dp))
                Nobby(MascotMood.YAY, 112.dp, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; rotationZ = -4f })
            }
            Text(
                stringResource(celebration.kind.doneTitle()),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.done_lead),
                Modifier.padding(top = 6.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (celebration.nextKind != null && celebration.nextAt != null) NextRow(celebration.nextKind, celebration.nextAt)
            LinearProgressIndicator(
                progress = { countdown.value },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(4.dp),
                color = c.outline,
                trackColor = c.surfaceContainerHighest,
            )
            Text(
                stringResource(R.string.done_autoclose),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextRow(kind: EventKind, at: LocalDateTime) {
    val icon = when (kind) {
        EventKind.REMOVE -> Icons.AutoMirrored.Rounded.Logout
        EventKind.INSERT -> Icons.AutoMirrored.Rounded.Login
        EventKind.SWAP -> Icons.Rounded.SwapHoriz
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, if (kind == EventKind.INSERT) TileTone.PAUSE else TileTone.WEAR, size = 40.dp)
        Text(stringResource(R.string.done_next, kind.label(), formatDateTime(at)), style = MaterialTheme.typography.bodyMedium)
    }
}
