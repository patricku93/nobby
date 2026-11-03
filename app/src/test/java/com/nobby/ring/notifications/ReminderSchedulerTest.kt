package com.nobby.ring.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var scheduler: ReminderScheduler
    private lateinit var schedule: ScheduleRepository
    private lateinit var alarmManager: AlarmManager
    private var nowValue = LocalDateTime(2026, 11, 2, 12, 0)
    private val clock = object : NobbyClock {
        override fun now() = nowValue
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "sched-${System.nanoTime()}.preferences_pb") }
        val configStore = MethodConfigStore(ds)
        schedule = ScheduleRepository(configStore, db.eventLogDao(), clock)
        val reminders = ReminderRepository(db.reminderRuleDao())
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 1))
        reminders.replaceAll(ReminderRule.defaults(LocalTime(21, 0)))
        scheduler = ReminderScheduler(context, schedule, reminders, OverdueConfigStore(ds), clock, ds, Notifier(context, ThemeStore(ds)))
    }

    private fun overdueAlarmTime(): Long? {
        val overdue = PendingIntent.getBroadcast(
            context, ReminderScheduler.OVERDUE_CODE, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_OVERDUE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return null
        return shadowOf(alarmManager).scheduledAlarms.singleOrNull { it.operation == overdue }?.triggerAtTime
    }

    @Test
    fun `rescheduleAll sets one alarm per planned reminder`() = runTest {
        scheduler.rescheduleAll()
        val alarms = shadowOf(alarmManager).scheduledAlarms
        // 1 month horizon: REMOVE (Nov 22) + INSERT (Nov 29) → 2 rules × 2 events = 4 reminders + 1 overdue alarm
        assertEquals(5, alarms.size)
        assertTrue(alarms.all { it.triggerAtTime > 0 })
    }

    @Test
    fun `rescheduleAll is idempotent`() = runTest {
        scheduler.rescheduleAll()
        scheduler.rescheduleAll()
        assertEquals(5, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `rescheduleAll cancels stale reminder alarms after the schedule changes`() = runTest {
        scheduler.rescheduleAll()
        assertEquals(5, shadowOf(alarmManager).scheduledAlarms.size)
        val removeId = schedule.currentState()!!.nextEvent!!.id
        schedule.confirm(removeId, LocalDateTime(2026, 11, 22, 21, 0), ShiftPolicy.KEEP)
        scheduler.rescheduleAll()
        // The REMOVE reminders are gone; 2 reminders for the INSERT + 1 overdue alarm remain.
        assertEquals(3, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `rescheduleAll keeps a pending overdue alarm for the same event`() = runTest {
        nowValue = LocalDateTime(2026, 11, 22, 23, 0)
        scheduler.rescheduleAll()
        val removeId = schedule.currentState()!!.nextEvent!!.id
        val nowMillis = nowValue.toInstant(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(nowMillis, overdueAlarmTime())

        scheduler.armOverdueIn(1, removeId) // snooze
        val snoozed = nowMillis + 60 * 60 * 1000L
        assertEquals(snoozed, overdueAlarmTime())

        scheduler.rescheduleAll()
        assertEquals(snoozed, overdueAlarmTime())
    }
    @Test
    fun `a waiting schedule keeps its overdue first-ring nag after a new time of day is applied (I1)`() = runTest {
        nowValue = LocalDateTime(2026, 11, 22, 23, 0)
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 22, 21, 0), 1, firstInsertPending = true))
        // Settings: routine time 21:00 -> 22:00, the start's time follows; still before now.
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(22, 0)), LocalDateTime(2026, 11, 22, 22, 0), 1, firstInsertPending = true))
        scheduler.rescheduleAll()
        assertEquals(true, schedule.currentState()!!.config.firstInsertPending)
        assertEquals(nowValue.toInstant(TimeZone.UTC).toEpochMilliseconds(), overdueAlarmTime())
    }
}
