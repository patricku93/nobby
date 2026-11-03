package com.nobby.ring.ui.settings

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import com.nobby.ring.data.calendar.CalendarSyncer
import com.nobby.ring.data.calendar.DeviceCalendar
import com.nobby.ring.data.calendar.FakeCalendarSource
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class CalendarSettingsViewModelTest {
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 2, 12, 0)
        override val zone = TimeZone.UTC
    }
    private lateinit var context: Context
    private lateinit var source: FakeCalendarSource
    private lateinit var store: CalendarSyncStore
    private lateinit var vm: CalendarSettingsViewModel
    private val privat = DeviceCalendar(2, "Privat", "a", 0xFF0000)
    private val arbeit = DeviceCalendar(3, "Arbeit", "b", 0x00FF00)

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "vm-${System.nanoTime()}.preferences_pb") }
        val schedule = ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock)
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2))
        store = CalendarSyncStore(ds)
        source = FakeCalendarSource()
        val syncer = CalendarSyncer(source, db.calendarLinkDao(), store, schedule, clock, context)
        vm = CalendarSettingsViewModel(context, source, syncer, store, clock, CoroutineScope(Dispatchers.Unconfined))
    }

    private suspend fun awaitState(predicate: (CalendarSettingsUiState) -> Boolean): CalendarSettingsUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.uiState.first(predicate) } }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `enabling with several calendars writes nothing until one is picked`() = runTest {
        source.calendars = listOf(privat, arbeit)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.refreshPermission()
        vm.setEnabled(true).join()
        assertTrue(store.enabled.first())
        assertNull(store.calendarId.first())
        assertNull(store.lastSync.first())
        assertEquals(0, source.events.size)

        vm.select(3).join()
        assertEquals(3L, store.calendarId.first())
        assertEquals(4, source.events.size)
    }

    @Test
    fun `enabling with exactly one calendar auto-selects it`() = runTest {
        source.calendars = listOf(privat)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.refreshPermission()
        vm.setEnabled(true).join()
        assertEquals(2L, store.calendarId.first())
        assertEquals(4, source.events.size)
    }

    @Test
    fun `a deleted selected calendar shows the gone message and is flagged, picking another recovers`() = runTest {
        source.calendars = listOf(privat, arbeit)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.refreshPermission()
        vm.select(3).join()
        val synced = store.lastSync.first()
        source.calendars = listOf(privat)
        vm.refreshPermission()
        vm.syncNow().join()
        val gone = awaitState { it.selectedMissing }
        // The "gone" text is derived from selectedMissing, not stored as a (stale, frozen-locale) error.
        assertNull(gone.error)
        assertTrue(gone.selectedMissing)
        assertEquals(synced, store.lastSync.first())

        vm.select(2).join()
        val recovered = awaitState { it.selectedId == 2L }
        assertFalse(recovered.selectedMissing)
    }

    @Test
    fun `a reused id with another identity is flagged missing and picking it again re-creates the events`() = runTest {
        source.calendars = listOf(privat)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.refreshPermission()
        vm.select(2).join()
        val synced = store.lastSync.first()
        assertEquals(4, source.events.size)
        // The calendar was deleted and a different one got id 2; its events died with the old one.
        source.events.clear(); source.calendarOf.clear()
        val replacement = DeviceCalendar(2, "Neu", "fremd@gmail.com", 0)
        source.calendars = listOf(replacement)
        vm.refreshPermission()
        vm.syncNow().join()
        assertTrue(awaitState { it.selectedMissing }.selectedMissing)
        assertEquals(0, source.events.size)
        assertEquals(synced, store.lastSync.first())

        vm.select(2).join()
        assertEquals(4, source.events.size)
        assertEquals(replacement.identity, store.calendarIdentity.first())
        assertFalse(awaitState { it.selectedId == 2L && it.calendars.isNotEmpty() }.selectedMissing)
    }

    @Test
    fun `needsChoice is true only while enabled without a selection`() {
        val d = LocalDate(2026, 11, 2)
        val base = CalendarSettingsUiState(from = d, to = d)
        assertTrue(base.copy(enabled = true).needsChoice)
        assertFalse(base.copy(enabled = true, selectedId = 2).needsChoice)
        assertFalse(base.needsChoice)
    }
}
