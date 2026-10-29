package com.nobby.ring.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
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
    private lateinit var alarmManager: AlarmManager
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 2, 12, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "sched-${System.nanoTime()}.preferences_pb") }
        val configStore = MethodConfigStore(ds)
        val schedule = ScheduleRepository(configStore, db.eventLogDao(), clock)
        val reminders = ReminderRepository(db.reminderRuleDao())
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 1))
        reminders.replaceAll(ReminderRule.defaults(LocalTime(21, 0)))
        scheduler = ReminderScheduler(context, schedule, reminders, OverdueConfigStore(ds), clock, ds)
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
}
