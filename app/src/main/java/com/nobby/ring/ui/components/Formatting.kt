package com.nobby.ring.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
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

fun formatDate(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

fun formatTime(time: LocalTime): String =
    time.toJavaLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

fun formatDateTime(dt: LocalDateTime): String = "${formatDate(dt.date)} ${formatTime(dt.time)}"

/** Localised duration for composables; recomposes on configuration (locale) changes. */
@Composable
fun formatDuration(d: Duration): String {
    LocalConfiguration.current
    return formatDuration(d, LocalContext.current.resources)
}

/** Localised duration for non-UI callers. */
fun formatDuration(d: Duration, res: Resources): String {
    val days = d.inWholeDays
    val hours = d.inWholeHours % 24
    val minutes = d.inWholeMinutes % 60
    return buildList {
        if (days > 0) add(res.getString(R.string.duration_days, days))
        if (hours > 0) add(res.getString(R.string.duration_hours, hours))
        if (days == 0L && minutes > 0) add(res.getString(R.string.duration_minutes, minutes))
    }.joinToString(" ").ifEmpty { res.getString(R.string.duration_minutes, 0L) }
}
