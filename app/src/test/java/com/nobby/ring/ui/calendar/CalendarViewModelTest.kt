package com.nobby.ring.ui.calendar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.NotesRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "cal-${System.nanoTime()}.preferences_pb") }
        vm = CalendarViewModel(ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock), NotesRepository(db.noteDao(), clock), clock)
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

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
