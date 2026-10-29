package com.nobby.ring.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-plans everything (alarms, widget, calendar) whenever armed alarms may be invalid: after boot or an app update
 * (alarms are cleared), after a wall-clock or time-zone change (RTC alarms point at the wrong instant), and when the
 * exact-alarm permission changes (alarms must switch between exact and inexact).
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var changeNotifier: ScheduleChangeNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { changeNotifier.notifyChanged() }.onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e("NoBby", "re-planning after ${intent.action} failed", e)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        /** `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+), spelled out for minSdk 26. */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
        )
    }
}
