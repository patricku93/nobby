package com.nobby.ring.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.PhaseKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration

@Composable fun EventKind.label(): String = stringResource(
    when (this) {
        EventKind.REMOVE -> R.string.event_remove
        EventKind.INSERT -> R.string.event_insert
        EventKind.SWAP -> R.string.event_swap
    },
)

@Composable fun PhaseKind.label(): String = stringResource(if (this == PhaseKind.WEAR) R.string.phase_wear else R.string.phase_pause)

@Composable fun Importance.label(): String = stringResource(
    when (this) {
        Importance.LOW -> R.string.importance_low
        Importance.NORMAL -> R.string.importance_normal
        Importance.HIGH -> R.string.importance_high
        Importance.URGENT -> R.string.importance_urgent
    },
)

@Composable fun Palette.label(): String = stringResource(
    when (this) {
        Palette.ROSE -> R.string.palette_rose
        Palette.LAVENDER -> R.string.palette_lavender
        Palette.MINT -> R.string.palette_mint
        Palette.OCEAN -> R.string.palette_ocean
        Palette.GRAPHITE -> R.string.palette_graphite
    },
)

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatDate(date: LocalDate): String = date.toJavaLocalDate().format(dateFmt)
fun formatTime(time: LocalTime): String = time.toJavaLocalTime().format(timeFmt)
fun formatDateTime(dt: LocalDateTime): String = "${formatDate(dt.date)} ${formatTime(dt.time)}"

fun formatDuration(d: Duration): String {
    val days = d.inWholeDays
    val hours = d.inWholeHours % 24
    val minutes = d.inWholeMinutes % 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (days == 0L && minutes > 0) add("${minutes}min")
    }.joinToString(" ").ifEmpty { "0min" }
}
