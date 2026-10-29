package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.ShiftPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var schedule: ScheduleRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var changeNotifier: ScheduleChangeNotifier
    @Inject lateinit var clock: NobbyClock

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    when (intent.action) {
                        ACTION_DONE -> {
                            val now = clock.now()
                            // A late removal keeps the usual full break; a late insertion keeps the planned removal.
                            val event = schedule.currentState()?.events?.firstOrNull { it.id == eventId }
                            val policy = event?.let { ConfirmAction.quickConfirmPolicy(it, now) } ?: ShiftPolicy.KEEP
                            schedule.confirm(eventId, now, policy)
                            notifier.cancelOverdue()
                            changeNotifier.notifyChanged()
                        }
                        ACTION_SNOOZE -> {
                            notifier.cancelOverdue()
                            scheduler.armOverdueIn(scheduler.snoozeHours(), eventId)
                        }
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e("NoBby", "notification action failed", e)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.nobby.ring.ACTION_DONE"
        const val ACTION_SNOOZE = "com.nobby.ring.ACTION_SNOOZE"
        const val EXTRA_EVENT_ID = "eventId"
    }
}
