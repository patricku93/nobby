package com.nobby.ring.ui.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.notifications.Notifier
import com.nobby.ring.notifications.ReminderScheduler
import com.nobby.ring.notifications.ScheduleChangeListener
import com.nobby.ring.notifications.ScheduleChangeNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: NobbyDatabase
    private lateinit var repo: ScheduleRepository
    private lateinit var context: Context
    private lateinit var ds: DataStore<Preferences>
    private var changes = 0
    private var nowValue = LocalDateTime(2026, 11, 24, 21, 0)
    private val clock = object : NobbyClock {
        override fun now() = nowValue
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "home-${System.nanoTime()}.preferences_pb") }
        repo = ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock)
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private fun vm() = HomeViewModel(
        context, repo, clock,
        ScheduleChangeNotifier(setOf(object : ScheduleChangeListener { override suspend fun onScheduleChanged() { changes++ } })),
        ReminderScheduler(context, repo, ReminderRepository(db.reminderRuleDao()), OverdueConfigStore(ds), clock, ds, Notifier(context)),
        Notifier(context),
    )

    @Test
    fun `an in-app done shows the celebration with the next event and dismiss clears it`() = runTest(dispatcher) {
        nowValue = LocalDateTime(2026, 11, 22, 21, 0) // on time: no dialog
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state != null }
        vm.onDoneTapped()
        val shown = vm.uiState.first { it.celebration != null }.celebration!!
        assertEquals(EventKind.REMOVE, shown.kind)
        assertEquals(EventKind.INSERT, shown.nextKind)
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), shown.nextAt)
        vm.dismissCelebration()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.celebration)
    }

    @Test
    fun `done on a late event opens the confirm dialog, SHIFT moves next insert`() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state != null }
        vm.onDoneTapped()
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.pendingConfirm)
        vm.onPolicyChosen(ShiftPolicy.SHIFT)
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state?.nextEvent?.kind == EventKind.INSERT }
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.pendingConfirm)
        val next = vm.uiState.value.state!!.nextEvent!!
        assertEquals(EventKind.INSERT, next.kind)
        assertEquals(LocalDateTime(2026, 12, 1, 21, 0), next.plannedAt)
        assertEquals(1, changes)
    }

    @Test
    fun `toggle skip pause flips next event to swap and back`() = runTest(dispatcher) {
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state != null }
        vm.toggleSkipPause(); dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state?.nextEvent?.kind == EventKind.SWAP }
        assertEquals(EventKind.SWAP, vm.uiState.value.state!!.nextEvent!!.kind)
        vm.uiState.first { !it.busy } // the commit flag clears after the config write; a toggle sent while busy is dropped
        vm.toggleSkipPause(); dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state?.nextEvent?.kind == EventKind.REMOVE }
        assertEquals(EventKind.REMOVE, vm.uiState.value.state!!.nextEvent!!.kind)
    }

    @Test
    fun `done tapped twice confirms once`() = runTest(dispatcher) {
        nowValue = LocalDateTime(2026, 11, 22, 21, 0)
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state != null }
        vm.onDoneTapped()
        vm.onDoneTapped()
        vm.uiState.first { it.state?.nextEvent?.kind == EventKind.INSERT }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, db.eventLogDao().allOnce().size)
        assertEquals(1, changes)
    }

    @Test
    fun `done is ignored while the next event is not yet due`() = runTest(dispatcher) {
        nowValue = LocalDateTime(2026, 11, 21, 21, 0) // REMOVE is planned for 2026-11-22: one day ahead
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state != null }
        vm.onDoneTapped()
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.pendingConfirm)
        assertEquals(0, db.eventLogDao().allOnce().size)
        assertEquals(0, changes)
    }

    @Test
    fun `listener failure does not stop other listeners`() = runTest {
        var count = 0
        val notifier = ScheduleChangeNotifier(linkedSetOf(
            object : ScheduleChangeListener { override suspend fun onScheduleChanged() { error("boom") } },
            object : ScheduleChangeListener { override suspend fun onScheduleChanged() { count++ } },
        ))
        notifier.notifyChanged()
        assertEquals(1, count)
    }
}
