package com.nobby.ring.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.notifications.ScheduleChangeNotifier
import com.nobby.ring.ui.theme.accent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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

class RingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val state = ep.schedule().currentState()
        val config = ep.widgetConfig().config.first()
        val theme = ep.theme().theme.first()
        val strings = WidgetStrings(
            nextAny = context.getString(R.string.widget_row_next_any),
            nextRemove = context.getString(R.string.widget_row_next_remove),
            nextInsert = context.getString(R.string.widget_row_next_insert),
            overdue = context.getString(R.string.widget_row_overdue),
            cycleDay = context.getString(R.string.widget_row_cycle_day),
            none = context.getString(R.string.widget_none),
            inDays = { context.getString(R.string.widget_in_days, it) },
            overdueDays = { context.getString(R.string.widget_overdue_days, it) },
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
        )
        val data = WidgetModel.build(state, config, strings)
        val dark = when (theme.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val accent = theme.palette.accent(dark)
        val bg = if (dark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        val fg = if (dark) Color(0xFFF2F2F2) else Color(0xFF1C1C1E)

        provideContent { WidgetContent(data, config.mode, accent, bg, fg) }
    }

    @Composable
    private fun WidgetContent(data: WidgetData, mode: WidgetMode, accent: Color, bg: Color, fg: Color) {
        val size = LocalSize.current
        val context = LocalContext.current
        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(bg)).cornerRadius(20.dp).padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mode == WidgetMode.SINGLE || size.height < MEDIUM.height) {
                val row = data.rows.first()
                Text(row.label, style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp))
                Text(
                    row.value,
                    style = TextStyle(
                        color = ColorProvider(if (row.urgent) URGENT else accent),
                        fontSize = if (size.height < MEDIUM.height) 20.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            } else {
                data.rows.forEach { row ->
                    Row(GlanceModifier.fillMaxWidth()) {
                        Text(row.label, GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(fg), fontSize = 12.sp))
                        Text(
                            row.value,
                            style = TextStyle(color = ColorProvider(if (row.urgent) URGENT else accent), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
            }
            if (data.dueEventId != null && size.height >= MEDIUM.height) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    context.getString(R.string.home_mark_done),
                    GlanceModifier.background(ColorProvider(accent)).cornerRadius(12.dp).padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable(actionRunCallback<DoneAction>()),
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 50.dp)
        val MEDIUM = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        private val URGENT = Color(0xFFD32F2F)
    }
}

class DoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val state = ep.schedule().currentState() ?: return
        val next = state.nextEvent ?: return
        ep.schedule().confirm(next.id, ep.clock().now(), ShiftPolicy.KEEP)
        ep.changeNotifier().notifyChanged()
    }
}
