package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var schedule: ScheduleRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    when (intent.action) {
                        ACTION_REMINDER -> notifier.showReminder(
                            EventKind.valueOf(intent.getStringExtra(EXTRA_KIND)!!),
                            LocalDateTime.parse(intent.getStringExtra(EXTRA_PLANNED_AT)!!),
                            Importance.valueOf(intent.getStringExtra(EXTRA_IMPORTANCE)!!),
                            intent.getStringExtra(EXTRA_EVENT_ID)!!,
                        )
                        ACTION_OVERDUE -> handleOverdue()
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e("NoBby", "alarm handling failed", e)
                }
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handleOverdue() {
        val state = schedule.currentState() ?: return
        val overdue = state.overdue
        if (overdue == null) {
            // Not (or no longer) overdue, e.g. the alarm fired early after a clock change: re-plan from the current
            // state, which clears the nag and re-arms the overdue alarm for the next event.
            notifier.cancelOverdue()
            scheduler.rescheduleAll()
            return
        }
        try {
            notifier.showOverdue(overdue.event.kind, overdue.event.plannedAt, overdue.event.id, overdue.safetyBreached)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // The nag chain must survive a failed notification, so log it and still re-arm below.
            Log.e("NoBby", "showing the overdue notification failed", e)
        }
        scheduler.armOverdueIn(scheduler.repeatIntervalHours(), overdue.event.id)
        // The widget only refreshes daily on its own; show the overdue state as soon as it begins.
        runCatching { widgetUpdater.updateAll() }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e("NoBby", "widget update after overdue failed", e)
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.nobby.ring.REMINDER"
        const val ACTION_OVERDUE = "com.nobby.ring.OVERDUE"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_KIND = "kind"
        const val EXTRA_PLANNED_AT = "plannedAt"
        const val EXTRA_IMPORTANCE = "importance"
    }
}
