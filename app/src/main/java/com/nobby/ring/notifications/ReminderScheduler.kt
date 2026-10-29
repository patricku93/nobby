package com.nobby.ring.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
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
    private val notifier: Notifier,
) : ScheduleChangeListener {
    private val alarmManager: AlarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val trackedKey = stringSetPreferencesKey("scheduled_alarm_ids")
    private val overdueArmedKey = stringPreferencesKey("overdue_armed_event_id")
    private val mutex = Mutex()

    override suspend fun onScheduleChanged() = rescheduleAll()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Cancels every reminder alarm set earlier and re-arms from the current state. The single overdue alarm is kept
     * when it already targets the same overdue event (so a snooze survives a reschedule). Safe to call repeatedly.
     */
    suspend fun rescheduleAll() = mutex.withLock {
        cancelTracked()
        val state = schedule.currentState()
        if (state == null || state.overdue == null) notifier.cancelOverdue()
        if (state == null) {
            dataStore.edit { it.remove(trackedKey) }
            cancelOverdueAlarm()
            return@withLock
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
        dataStore.edit { it[trackedKey] = tracked }
        syncOverdueAlarm(state.nextEvent?.let { it.id to it.plannedAt }, now)
    }

    /** Re-arms the single overdue alarm [hours] from now (used for the repeat nag and for snooze). */
    suspend fun armOverdueIn(hours: Int, eventId: String) = mutex.withLock {
        val at = clock.now().toInstant(clock.zone) + hours.hours
        setAt(OVERDUE_CODE, overdueIntent(eventId), at.toEpochMilliseconds())
        dataStore.edit { it[overdueArmedKey] = eventId }
    }

    /** Cancels the armed overdue alarm (if any) and forgets its event id. */
    suspend fun clearOverdueAlarm() = mutex.withLock { cancelOverdueAlarm() }

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
            // The overdue alarm is managed by syncOverdueAlarm so that a pending snooze survives a reschedule.
            if (action == AlarmReceiver.ACTION_OVERDUE) return@forEach
            val code = entry.substringAfter('|').toIntOrNull() ?: return@forEach
            val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
            val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            pi?.let { alarmManager.cancel(it); it.cancel() }
        }
    }

    /**
     * Arms the overdue alarm for [next] (id to plannedAt), or clears it when there is none. An already-overdue event
     * whose alarm is still pending for the same event id keeps that alarm, so a snooze is not turned into an immediate nag.
     */
    private suspend fun syncOverdueAlarm(next: Pair<String, LocalDateTime>?, now: LocalDateTime) {
        if (next == null) {
            cancelOverdueAlarm()
            return
        }
        val (eventId, plannedAt) = next
        val armedId = dataStore.data.first()[overdueArmedKey]
        val keepExisting = plannedAt <= now && armedId == eventId && pendingOverdue(eventId) != null
        if (keepExisting) return
        val fireAt = if (plannedAt > now) plannedAt else now
        setAt(OVERDUE_CODE, overdueIntent(eventId), fireAt.toInstant(clock.zone).toEpochMilliseconds())
        dataStore.edit { it[overdueArmedKey] = eventId }
    }

    /** The pending overdue alarm's PendingIntent, or null when none is armed (extras are ignored when matching). */
    private fun pendingOverdue(eventId: String): PendingIntent? = PendingIntent.getBroadcast(
        context, OVERDUE_CODE, overdueIntent(eventId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun cancelOverdueAlarm() {
        pendingOverdue(dataStore.data.first()[overdueArmedKey].orEmpty())?.let { alarmManager.cancel(it); it.cancel() }
        dataStore.edit { it.remove(overdueArmedKey) }
    }

    private fun trackingEntry(action: String, code: Int) = "$action|$code"

    companion object {
        const val OVERDUE_CODE = 7_000_001
    }
}
