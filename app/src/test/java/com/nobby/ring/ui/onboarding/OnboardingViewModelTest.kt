package com.nobby.ring.ui.onboarding

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.nobby.ring.domain.engine.CycleCalculator
import com.nobby.ring.domain.engine.SetupStatus
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.notifications.AlarmReceiver
import com.nobby.ring.notifications.Notifier
import com.nobby.ring.notifications.ReminderScheduler
import com.nobby.ring.notifications.ScheduleChangeListener
import com.nobby.ring.notifications.ScheduleChangeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var configStore: MethodConfigStore
    private lateinit var themeStore: ThemeStore
    private lateinit var reminders: ReminderRepository
    private lateinit var schedule: ScheduleRepository
    private lateinit var notifier: ScheduleChangeNotifier
    private lateinit var db: NobbyDatabase
    @Volatile private var changes = 0
    private var nowValue = LocalDateTime(2026, 11, 5, 10, 0)
    private val clock = object : NobbyClock {
        override fun now() = nowValue
        override val zone = TimeZone.UTC
    }
    private val alarmManager get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "onb-${System.nanoTime()}.preferences_pb") }
        configStore = MethodConfigStore(ds)
        themeStore = ThemeStore(ds)
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        reminders = ReminderRepository(db.reminderRuleDao())
        schedule = ScheduleRepository(configStore, db.eventLogDao(), clock)
        val scheduler = ReminderScheduler(context, schedule, reminders, OverdueConfigStore(ds), clock, ds, Notifier(context, themeStore))
        notifier = ScheduleChangeNotifier(setOf(scheduler, object : ScheduleChangeListener {
            override suspend fun onScheduleChanged() { changes++ }
        }))
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private fun vm() = OnboardingViewModel(configStore, reminders, themeStore, schedule, notifier, CoroutineScope(dispatcher), clock)

    private fun OnboardingViewModel.answer(status: SetupStatus, at: LocalDateTime) {
        setStatus(status); setStatusDate(at.date); setStatusTime(at.time)
    }

    /** Finishes onboarding and returns the saved schedule state (DataStore writes run on a real IO thread). */
    private suspend fun OnboardingViewModel.finishAndLoad(): ScheduleState {
        finish()
        dispatcher.scheduler.advanceUntilIdle()
        state.first { it.finished }
        return schedule.currentState()!!
    }

    private fun overdueAlarmAt(): Long? {
        val overdue = PendingIntent.getBroadcast(
            context, ReminderScheduler.OVERDUE_CODE, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_OVERDUE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return null
        return shadowOf(alarmManager).scheduledAlarms.singleOrNull { it.operation == overdue }?.triggerAtTime
    }

    private fun millis(at: LocalDateTime) = at.toInstant(TimeZone.UTC).toEpochMilliseconds()

    /** Reminders armed, and the single overdue alarm waits for the next event instead of firing now. */
    private fun assertArmedFor(state: ScheduleState) {
        assertTrue(shadowOf(alarmManager).scheduledAlarms.size > 1)
        assertEquals(millis(state.nextEvent!!.plannedAt), overdueAlarmAt())
    }

    @Test
    fun `finish saves config, default reminders, theme and marks onboarding done`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_IN, LocalDateTime(2026, 11, 1, 21, 0))
        vm.setWear(21); vm.setPause(7); vm.setTime(LocalTime(21, 0))
        vm.setPalette(Palette.MATCHA)
        vm.setMode(ThemeMode.DARK)
        vm.finishAndLoad()

        val config = configStore.config.first()!!
        assertEquals(LocalDateTime(2026, 11, 1, 21, 0), config.anchor)
        assertEquals(21, config.method.wearDays)
        assertTrue(configStore.onboardingDone.first())
        assertEquals(2, reminders.rules.first().size)
        assertEquals(Palette.MATCHA, themeStore.theme.first().palette)
        assertEquals(ThemeMode.DARK, themeStore.theme.first().mode)
        assertEquals(1, changes) // the schedule is armed (reminders, widget, calendar) right after onboarding
    }

    @Test
    fun `ring in starts the wear phase with the removal next`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_IN, LocalDateTime(2026, 11, 1, 21, 0))
        val s = vm.finishAndLoad()
        assertEquals(PhaseKind.WEAR, s.position.phase)
        assertTrue(s.position.ringIn)
        assertEquals(EventKind.REMOVE, s.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 11, 22, 21, 0), s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
        assertArmedFor(s)
    }

    @Test
    fun `ring in older than the wear days leaves the removal overdue as before`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_IN, LocalDateTime(2026, 10, 1, 21, 0))
        val s = vm.finishAndLoad()
        assertEquals(EventKind.REMOVE, s.overdue!!.event.kind)
        assertEquals(LocalDateTime(2026, 10, 22, 21, 0), s.overdue!!.event.plannedAt)
    }

    @Test
    fun `ring out logs this cycle's removal and counts the pause down to the insertion`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_OUT, LocalDateTime(2026, 11, 3, 21, 0))
        vm.next()
        assertEquals(1, vm.state.value.step)
        val s = vm.finishAndLoad()
        assertEquals(LocalDateTime(2026, 10, 13, 21, 0), s.config.anchor)
        val removal = s.logs.single()
        assertEquals(EventKind.REMOVE, removal.kind)
        assertEquals(CycleCalculator.currentRemoveId(s.config), removal.eventId)
        assertEquals(LocalDateTime(2026, 11, 3, 21, 0), removal.confirmedAt)
        assertEquals(PhaseKind.PAUSE, s.position.phase)
        assertFalse(s.position.ringIn)
        assertEquals(EventKind.INSERT, s.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 11, 10, 21, 0), s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
        assertArmedFor(s)
    }

    @Test
    fun `ring out uses the lengths picked after the status step`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_OUT, LocalDateTime(2026, 11, 3, 21, 0))
        vm.next()
        vm.setWear(24); vm.setPause(5)
        val s = vm.finishAndLoad()
        assertEquals(LocalDateTime(2026, 10, 10, 21, 0), s.config.anchor)
        assertEquals(LocalDateTime(2026, 11, 8, 21, 0), s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
    }

    @Test
    fun `ring out exactly one pause ago is due now, not overdue`() = runTest(dispatcher) {
        nowValue = LocalDateTime(2026, 11, 5, 21, 0)
        val vm = vm()
        vm.answer(SetupStatus.RING_OUT, LocalDateTime(2026, 10, 29, 21, 0))
        vm.next()
        assertEquals(1, vm.state.value.step)
        val s = vm.finishAndLoad()
        assertEquals(EventKind.INSERT, s.nextEvent!!.kind)
        assertEquals(nowValue, s.nextEvent!!.plannedAt)
        assertEquals(0, s.daysUntilNext)
        assertNull(s.overdue)
    }

    @Test
    fun `ring out with the pause already over asks for the planned insertion instead`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_OUT, LocalDateTime(2026, 10, 25, 21, 0))
        vm.next()
        val st = vm.state.value
        assertEquals(0, st.step)
        assertTrue(st.pauseOver)
        assertEquals(SetupStatus.NOT_STARTED, st.status)

        vm.setStatusDate(LocalDate(2026, 11, 6)); vm.setStatusTime(LocalTime(21, 0))
        vm.next()
        assertEquals(1, vm.state.value.step)
        val s = vm.finishAndLoad()
        assertFalse(s.position.started)
        assertEquals(EventKind.INSERT, s.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 11, 6, 21, 0), s.nextEvent!!.plannedAt)
        assertTrue(s.logs.isEmpty())
        assertNull(s.overdue)
    }

    @Test
    fun `a pause shortened later that is then over sends finish back to the status step`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.RING_OUT, LocalDateTime(2026, 11, 1, 21, 0))
        vm.next()
        vm.setPause(3)
        vm.finish()
        dispatcher.scheduler.advanceUntilIdle()
        val st = vm.state.value
        assertFalse(st.finished)
        assertFalse(st.saving)
        assertEquals(0, st.step)
        assertTrue(st.pauseOver)
        assertNull(configStore.config.first())
    }

    @Test
    fun `not started today waits for the insertion today`() = runTest(dispatcher) {
        val vm = vm()
        vm.answer(SetupStatus.NOT_STARTED, LocalDateTime(2026, 11, 5, 21, 0))
        val s = vm.finishAndLoad()
        assertFalse(s.position.started)
        assertFalse(s.position.ringIn)
        assertEquals(EventKind.INSERT, s.nextEvent!!.kind)
        assertEquals(0, s.daysUntilNext)
        assertNull(s.overdue)
        assertArmedFor(s)
    }

    @Test
    fun `not started in three days counts down to the first insertion with no removal before it`() = runTest(dispatcher) {
        val vm = vm()
        val start = LocalDateTime(2026, 11, 8, 21, 0)
        vm.answer(SetupStatus.NOT_STARTED, start)
        val s = vm.finishAndLoad()
        assertFalse(s.position.started)
        assertEquals(PhaseKind.PAUSE, s.position.phase)
        assertEquals(EventKind.INSERT, s.nextEvent!!.kind)
        assertEquals(start, s.nextEvent!!.plannedAt)
        assertEquals(3, s.daysUntilNext)
        assertTrue(s.pendingEvents.none { it.kind == EventKind.REMOVE && it.plannedAt <= start })
        assertNull(s.overdue)
        assertArmedFor(s)
    }

    @Test
    fun `future insertion or removal and a past first insertion are rejected`() {
        val vm = vm()
        vm.answer(SetupStatus.RING_IN, LocalDateTime(2026, 11, 6, 21, 0))
        assertEquals(StatusIssue.IN_FUTURE, vm.state.value.statusIssue)
        assertFalse(vm.state.value.canNext)
        vm.next()
        assertEquals(0, vm.state.value.step)

        vm.answer(SetupStatus.RING_OUT, LocalDateTime(2026, 11, 5, 11, 0))
        assertEquals(StatusIssue.IN_FUTURE, vm.state.value.statusIssue)
        vm.next()
        assertEquals(0, vm.state.value.step)

        vm.answer(SetupStatus.NOT_STARTED, LocalDateTime(2026, 11, 4, 21, 0))
        assertEquals(StatusIssue.IN_PAST, vm.state.value.statusIssue)
        vm.next()
        assertEquals(0, vm.state.value.step)

        vm.setStatusDate(LocalDate(2026, 11, 5))
        assertNull(vm.state.value.statusIssue)
        assertTrue(vm.state.value.canNext)
    }

    @Test
    fun `the status step needs an answer before going on`() {
        val vm = vm()
        assertNull(vm.state.value.status)
        assertFalse(vm.state.value.canNext)
        vm.next()
        assertEquals(0, vm.state.value.step)
    }

    @Test
    fun `default dates are valid for the chosen answer`() {
        val vm = vm() // 10:00, routine time 21:00
        vm.setStatus(SetupStatus.RING_IN)
        assertNull(vm.state.value.statusIssue)
        vm.setStatus(SetupStatus.NOT_STARTED)
        assertNull(vm.state.value.statusIssue)
        assertNotNull(vm.state.value.status)
    }

    @Test
    fun `onboarding has four steps`() {
        val vm = vm()
        vm.answer(SetupStatus.RING_IN, LocalDateTime(2026, 11, 1, 21, 0))
        repeat(6) { vm.next() }
        assertEquals(3, vm.state.value.step)
        assertEquals(4, OnboardingState.STEPS)
    }

    @Test
    fun `warnings appear above safety thresholds`() {
        val vm = vm()
        vm.setWear(30); vm.setPause(9)
        assertTrue(vm.state.value.wearWarning)
        assertTrue(vm.state.value.pauseWarning)
    }
}
