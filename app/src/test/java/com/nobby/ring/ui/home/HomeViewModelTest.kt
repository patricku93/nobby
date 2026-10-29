package com.nobby.ring.ui.home

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
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
    private var changes = 0
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 24, 21, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "home-${System.nanoTime()}.preferences_pb") }
        repo = ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock)
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    private fun vm() = HomeViewModel(repo, clock, ScheduleChangeNotifier(setOf(object : ScheduleChangeListener {
        override suspend fun onScheduleChanged() { changes++ }
    })))

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
        vm.toggleSkipPause(); dispatcher.scheduler.advanceUntilIdle()
        vm.uiState.first { it.state?.nextEvent?.kind == EventKind.REMOVE }
        assertEquals(EventKind.REMOVE, vm.uiState.value.state!!.nextEvent!!.kind)
    }
}
