package com.nobby.ring.ui.calendar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.NotesRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: NobbyDatabase
    private lateinit var vm: CalendarViewModel
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 10, 12, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        // Open it here, on the test thread: a lazy first open (onOpen -> invalidation tracker) on a Room IO thread
        // can race tearDown's close() and deadlock the test JVM.
        db.openHelper.writableDatabase
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "cal-${System.nanoTime()}.preferences_pb") }
        vm = CalendarViewModel(ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock), NotesRepository(db.noteDao(), clock), clock)
    }

    @After
    fun tearDown() {
        // Stop every ViewModel coroutine (stateIn sharing, debounced note save) and let the cancellations run,
        // then wait until Room's IO executors are idle. Closing the database while a query is still in flight
        // on those threads can block close() forever and hang the whole test JVM.
        vm.viewModelScope.cancel()
        dispatcher.scheduler.advanceUntilIdle()
        awaitIdle(db.queryExecutor)
        awaitIdle(db.transactionExecutor)
        Dispatchers.resetMain()
        db.close()
    }

    private fun awaitIdle(executor: Executor) {
        val done = CountDownLatch(1)
        executor.execute { done.countDown() }
        check(done.await(10, TimeUnit.SECONDS)) { "Room executor did not drain" }
    }

    @Test
    fun `go to today returns to the current month and selects today`() = runTest(dispatcher) {
        backgroundScope.launch { vm.uiState.collect {} }
        vm.nextMonth()
        vm.nextMonth()
        vm.select(LocalDate(2027, 1, 5))
        dispatcher.scheduler.advanceUntilIdle()
        vm.goToToday()
        val ui = vm.uiState.first { it.month == 11 && it.selected == LocalDate(2026, 11, 10) }
        assertEquals(2026, ui.year)
    }
}
