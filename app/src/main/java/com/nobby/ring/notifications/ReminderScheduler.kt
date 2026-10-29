package com.nobby.ring.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toInstant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val schedule: ScheduleRepository,
    private val reminders: ReminderRepository,
    private val overdueConfig: OverdueConfigStore,
    private val clock: NobbyClock,
    private val dataStore: DataStore<Preferences>,
) : ScheduleChangeListener {
    private val alarmManager: AlarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val trackedKey = stringSetPreferencesKey("scheduled_alarm_ids")

    override suspend fun onScheduleChanged() = rescheduleAll()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Cancels every alarm set earlier, then re-arms from the current state. Safe to call repeatedly. */
    suspend fun rescheduleAll() {
        cancelTracked()
        val state = schedule.currentState()
        if (state == null) {
            dataStore.edit { it.remove(trackedKey) }
            return
        }
        val now = clock.now()
        val eventsById = state.events.associateBy { it.id }
        val tracked = mutableSetOf<String>()

        ReminderPlanner.plan(reminders.rulesOnce(), state.events, state.logs, now).forEach { r ->
            val plannedAt = eventsById[r.eventId]?.plannedAt ?: return@forEach
            val code = (r.fireAt.hashCode() * 31) xor r.ruleId.toInt()
            val intent = Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_REMINDER)
                .putExtra(AlarmReceiver.EXTRA_EVENT_ID, r.eventId)
                .putExtra(AlarmReceiver.EXTRA_KIND, r.kind.name)
                .putExtra(AlarmReceiver.EXTRA_PLANNED_AT, plannedAt.toString())
                .putExtra(AlarmReceiver.EXTRA_IMPORTANCE, r.importance.name)
            setAt(code, intent, r.fireAt.toInstant(clock.zone).toEpochMilliseconds())
            tracked += trackingEntry(AlarmReceiver.ACTION_REMINDER, code)
        }
        state.nextEvent?.let { next ->
            val fireAt = if (next.plannedAt > now) next.plannedAt else now
            setAt(OVERDUE_CODE, overdueIntent(next.id), fireAt.toInstant(clock.zone).toEpochMilliseconds())
            tracked += trackingEntry(AlarmReceiver.ACTION_OVERDUE, OVERDUE_CODE)
        }
        dataStore.edit { it[trackedKey] = tracked }
    }

    /** Re-arms the single overdue alarm [hours] from now (used for the repeat nag and for snooze). */
    fun armOverdueIn(hours: Int, eventId: String) {
        val at = clock.now().toInstant(clock.zone) + hours.hours
        setAt(OVERDUE_CODE, overdueIntent(eventId), at.toEpochMilliseconds())
    }

    suspend fun repeatIntervalHours(): Int = overdueConfig.config.first().repeatEveryHours

    suspend fun snoozeHours(): Int = overdueConfig.config.first().snoozeHours

    private fun overdueIntent(eventId: String) = Intent(context, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_OVERDUE)
        .putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)

    private fun setAt(code: Int, intent: Intent, millis: Long) {
        val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private suspend fun cancelTracked() {
        dataStore.data.first()[trackedKey].orEmpty().forEach { entry ->
            val action = entry.substringBefore('|')
            val code = entry.substringAfter('|').toIntOrNull() ?: return@forEach
            val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
            val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            pi?.let { alarmManager.cancel(it); it.cancel() }
        }
    }

    private fun trackingEntry(action: String, code: Int) = "$action|$code"

    companion object {
        const val OVERDUE_CODE = 7_000_001
    }
}
