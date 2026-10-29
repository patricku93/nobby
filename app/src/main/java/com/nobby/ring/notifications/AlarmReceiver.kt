package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
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
            // Not (or no longer) overdue: clear the nag and do not re-arm; rescheduleAll re-arms for the next event.
            notifier.cancelOverdue()
            return
        }
        notifier.showOverdue(overdue.event.kind, overdue.event.plannedAt, overdue.event.id, overdue.safetyBreached)
        scheduler.armOverdueIn(scheduler.repeatIntervalHours(), overdue.event.id)
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
