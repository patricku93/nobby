package com.nobby.ring.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nobby.ring.MainActivity
import com.nobby.ring.R
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.notifications.ScheduleChangeNotifier
import com.nobby.ring.ui.components.RingTone
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.ring.RingLabel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun schedule(): ScheduleRepository
    fun widgetConfig(): WidgetConfigStore
    fun theme(): ThemeStore
    fun clock(): NobbyClock
    fun changeNotifier(): ScheduleChangeNotifier
}

private data class WidgetLabels(val done: String, val appName: String)

class RingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val initialState = ep.schedule().currentState()
        val initialConfig = ep.widgetConfig().config.first()
        val initialTheme = ep.theme().theme.first()
        val strings = widgetStrings(context)
        val labels = WidgetLabels(done = context.getString(R.string.home_mark_done), appName = context.getString(R.string.app_name))
        // Collect the stores inside the composition: an existing Glance session does not re-run
        // provideGlance on updateAll(), so values captured above would go stale (config/theme changes lagged by one update).
        provideContent {
            val state by ep.schedule().state.collectAsState(initialState)
            val config by ep.widgetConfig().config.collectAsState(initialConfig)
            val theme by ep.theme().theme.collectAsState(initialTheme)
            val data = WidgetModel.build(state, config, strings)
            val palette = WidgetPalette(theme.palette.widgetColors(false), theme.palette.widgetColors(true), theme.mode)
            WidgetContent(data, config.mode, palette, labels)
        }
    }

    @Composable
    private fun WidgetContent(data: WidgetData, mode: WidgetMode, p: WidgetPalette, labels: WidgetLabels) {
        val size = LocalSize.current
        val small = size.height < MEDIUM.height
        Box(
            GlanceModifier.fillMaxSize().background(p.of { it.bg }).cornerRadius(24.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            when {
                mode == WidgetMode.MULTI -> MultiLayout(data, p, labels, small)
                small -> SmallLayout(data, p)
                size.width < WIDE.width -> SquareLayout(data, p, labels)
                else -> WideLayout(data, p, labels)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 50.dp)
        val MEDIUM = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

private fun text(color: ColorProvider, size: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif)

private fun WidgetData.urgent(): Boolean = tone == RingTone.OVERDUE || tone == RingTone.SAFETY
private fun WidgetData.head(): WidgetHeadline = headline ?: WidgetHeadline("–", "")
private fun WidgetData.numberSize(): Int = if (head().number.length > 3) 30 else 44
private fun WidgetData.barPick(): (WidgetColors) -> Color = when {
    urgent() -> { c -> c.err }
    tone == RingTone.PAUSE -> { c -> c.pause }
    else -> { c -> c.hi }
}

/** 2x1: number + label side by side. */
@Composable
private fun SmallLayout(d: WidgetData, p: WidgetPalette) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(d.head().number, style = text(p.of { if (d.urgent()) it.err else it.fg }, if (d.numberSize() == 44) 36 else 26, FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.width(10.dp))
        Text(d.head().label, style = text(p.of { it.sub }, 13), maxLines = 2)
    }
}

/** 2x2: day label + face, big number, label + phase bar, or the Done pill when due/overdue. */
@Composable
private fun SquareLayout(d: WidgetData, p: WidgetPalette, labels: WidgetLabels) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Two lines beside the 40 dp face: "Tag 21/21" or the overdue unit never get cut at 2 columns.
            Text(d.dayLabel, GlanceModifier.defaultWeight(), style = text(p.of { if (d.urgent()) it.err else it.sub }, 13), maxLines = 2)
            WidgetFace(d.mood, p, 40.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(d.head().number, style = text(p.of { if (d.urgent()) it.err else it.fg }, d.numberSize(), FontWeight.Bold), maxLines = 1)
        if (d.dueEventId != null) {
            DonePill(labels.done, d.urgent(), p)
        } else {
            Text(d.head().label, style = text(p.of { it.sub }, 13), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            PhaseBar(d, p)
        }
    }
}

/** 4x2: number block on the left, face + bar (or Done) on the right. */
@Composable
private fun WideLayout(d: WidgetData, p: WidgetPalette, labels: WidgetLabels) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            // While overdue the day label is the unit, which the label below already shows.
            if (!d.urgent()) Text(d.dayLabel, style = text(p.of { it.sub }, 13), maxLines = 1)
            Text(d.head().number, style = text(p.of { if (d.urgent()) it.err else it.fg }, d.numberSize(), FontWeight.Bold), maxLines = 1)
            Text(d.head().label, style = text(p.of { it.sub }, 13), maxLines = 2)
        }
        Spacer(GlanceModifier.width(16.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.defaultWeight())
                WidgetFace(d.mood, p, 40.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (d.dueEventId != null) DonePill(labels.done, d.urgent(), p) else PhaseBar(d, p)
        }
    }
}

/** Multi: up to three rows with a colour dot; header with the face from 2x2 up; Done when due/overdue. */
@Composable
private fun MultiLayout(d: WidgetData, p: WidgetPalette, labels: WidgetLabels, small: Boolean) {
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (!small) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(labels.appName, GlanceModifier.defaultWeight(), style = text(p.of { it.fg }, 14, FontWeight.Bold), maxLines = 1)
                WidgetFace(d.mood, p, 28.dp)
            }
        }
        d.rows.take(if (small) 2 else WidgetConfig.MAX_ROWS).forEach { row ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(p.of { if (row.urgent) it.err else it.hi })) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(row.label, GlanceModifier.defaultWeight(), style = text(p.of { it.fg }, 14), maxLines = 1)
                Text(row.value, style = text(p.of { if (row.urgent) it.err else it.fg }, 15, FontWeight.Bold), maxLines = 1)
            }
        }
        if (!small && d.dueEventId != null) {
            Spacer(GlanceModifier.height(6.dp))
            DonePill(labels.done, d.urgent(), p)
        }
    }
}

@Composable
private fun PhaseBar(d: WidgetData, p: WidgetPalette) {
    LinearProgressIndicator(
        progress = d.progress,
        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
        color = p.of(d.barPick()),
        backgroundColor = p.of { it.track },
    )
}

@Composable
private fun DonePill(label: String, urgent: Boolean, p: WidgetPalette) {
    Box(
        GlanceModifier.fillMaxWidth().height(36.dp).cornerRadius(18.dp)
            .background(p.of { if (urgent) it.err else it.hi })
            .clickable(actionRunCallback<DoneAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = text(p.of { if (urgent) it.onErr else it.onHi }, 14, FontWeight.Bold), maxLines = 1)
    }
}

/** Flat NoBby face: white VectorDrawable masks tinted with the preset's mascot, cheek and ink colours. */
@Composable
private fun WidgetFace(mood: MascotMood, p: WidgetPalette, size: Dp) {
    Box(GlanceModifier.size(size)) {
        Image(ImageProvider(R.drawable.widget_nobby_body), null, GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(p.of { it.mascotBody }))
        if (mood != MascotMood.WORRIED && mood != MascotMood.ALERT) {
            Image(ImageProvider(R.drawable.widget_nobby_cheeks), null, GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(p.of { it.mascotCheek }))
        }
        Image(ImageProvider(faceRes(mood)), null, GlanceModifier.fillMaxSize(), colorFilter = ColorFilter.tint(p.of { it.ink }))
    }
}

private fun faceRes(mood: MascotMood): Int = when (mood) {
    MascotMood.HAPPY, MascotMood.YAY -> R.drawable.widget_nobby_face_happy
    MascotMood.NEUTRAL -> R.drawable.widget_nobby_face_neutral
    MascotMood.SLEEPY -> R.drawable.widget_nobby_face_sleepy
    MascotMood.WORRIED -> R.drawable.widget_nobby_face_worried
    MascotMood.ALERT -> R.drawable.widget_nobby_face_alert
}

private fun widgetStrings(context: Context): WidgetStrings {
    val res = context.resources
    fun q(id: Int, n: Int): String = res.getQuantityString(id, n)
    fun qn(id: Int, n: Int): String = res.getQuantityString(id, n, n)
    return WidgetStrings(
        nextAny = context.getString(R.string.widget_row_next_any),
        nextRemove = context.getString(R.string.widget_row_next_remove),
        nextInsert = context.getString(R.string.widget_row_next_insert),
        overdue = context.getString(R.string.widget_row_overdue),
        cycleDay = context.getString(R.string.widget_row_cycle_day),
        none = context.getString(R.string.widget_none),
        inDays = { qn(R.plurals.widget_in_days, it) },
        overdueDays = { qn(R.plurals.widget_overdue_days, it) },
        overdueHours = { qn(R.plurals.widget_overdue_hours, it) },
        cycleDayFmt = { d, n -> context.getString(R.string.cycle_day, d, n) },
        kindLabel = { k ->
            context.getString(
                when (k) {
                    EventKind.REMOVE -> R.string.event_remove
                    EventKind.INSERT -> R.string.event_insert
                    EventKind.SWAP -> R.string.event_swap
                },
            )
        },
        ringLabel = { label, n ->
            when (label) {
                RingLabel.UNTIL_REMOVE -> q(R.plurals.ring_until_remove, n)
                RingLabel.UNTIL_INSERT -> q(R.plurals.ring_until_insert, n)
                RingLabel.UNTIL_SWAP -> q(R.plurals.ring_until_swap, n)
                RingLabel.OVERDUE_DAYS -> q(R.plurals.ring_overdue_days, n)
                RingLabel.OVERDUE_HOURS -> q(R.plurals.ring_overdue_hours, n)
                RingLabel.TODAY, RingLabel.NONE -> ""
            }
        },
        today = context.getString(R.string.ring_today),
        dayOf = { d, n -> context.getString(R.string.widget_day_of, d, n) },
    )
}

class DoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runCatching {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val state = ep.schedule().currentState() ?: return
            // The widget may be stale: re-check that the event is still due today (or overdue) before confirming.
            val days = state.daysUntilNext ?: return
            if (days > 0) return
            val next = state.nextEvent ?: return
            val now = ep.clock().now()
            // A late removal keeps the usual full break; a late insertion keeps the planned removal.
            ep.schedule().confirm(next.id, now, ConfirmAction.quickConfirmPolicy(next, now))
            ep.changeNotifier().notifyChanged()
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e("NoBby", "widget done action failed", e)
        }
    }
}
