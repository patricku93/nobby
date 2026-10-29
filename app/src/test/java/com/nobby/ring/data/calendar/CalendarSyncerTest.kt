package com.nobby.ring.data.calendar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.CalendarLinkDao
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.CalendarEntry
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

class FakeCalendarSource : CalendarSource {
    val events = linkedMapOf<Long, CalendarEntry>()
    private var nextId = 100L
    override suspend fun listWritable() = listOf(DeviceCalendar(1, "Test", "test@example.com", 0))
    override suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long { val id = nextId++; events[id] = entry; return id }
    override suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone) { events[providerId] = entry }
    override suspend fun delete(providerId: Long): Boolean = events.remove(providerId) != null
    override suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long) =
        events.filter { pattern.containsMatchIn(it.value.title) }.map { FoundEvent(it.key, it.value.title, 0L) }
}

@RunWith(RobolectricTestRunner::class)
class CalendarSyncerTest {
    private lateinit var syncer: CalendarSyncer
    private lateinit var source: FakeCalendarSource
    private lateinit var schedule: ScheduleRepository
    private lateinit var linkDao: CalendarLinkDao
    private lateinit var syncStore: CalendarSyncStore
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 2, 12, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "cal-${System.nanoTime()}.preferences_pb") }
        schedule = ScheduleRepository(MethodConfigStore(ds), db.eventLogDao(), clock)
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 2))
        syncStore = CalendarSyncStore(ds)
        linkDao = db.calendarLinkDao()
        syncStore.save(enabled = true, calendarId = 1)
        source = FakeCalendarSource()
        syncer = CalendarSyncer(source, linkDao, syncStore, schedule, clock, context)
    }

    @Test
    fun `first sync creates one provider event per pending event`() = runTest {
        val created = syncer.syncNow().getOrThrow()
        assertEquals(4, created)
        assertEquals(4, source.events.size)
    }

    @Test
    fun `second sync changes nothing, config change updates in place`() = runTest {
        syncer.syncNow()
        assertEquals(0, syncer.syncNow().getOrThrow())
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(8, 0)), LocalDateTime(2026, 11, 1, 8, 0), 2))
        syncer.syncNow()
        assertEquals(4, source.events.size)
        assertEquals(8, source.events.values.first().startAt.hour)
    }

    @Test
    fun `disconnect removes everything`() = runTest {
        syncer.syncNow()
        syncer.disconnect()
        assertEquals(0, source.events.size)
    }

    @Test
    fun `forget drops links for the given provider ids`() = runTest {
        syncer.syncNow()
        val firstProviderId = linkDao.allOnce().first().providerEventId
        source.delete(firstProviderId)
        syncer.forget(listOf(firstProviderId))
        assertEquals(3, linkDao.allOnce().size)
        assertEquals(1, syncer.syncNow().getOrThrow())
        assertEquals(4, source.events.size)
    }

    @Test
    fun `clearLinks removes events and links but keeps the store enabled`() = runTest {
        syncer.syncNow()
        syncer.clearLinks()
        assertEquals(0, source.events.size)
        assertEquals(0, linkDao.allOnce().size)
        assertEquals(true, syncStore.enabled.first())
    }
}
