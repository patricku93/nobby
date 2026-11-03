package com.nobby.ring.data.calendar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import com.nobby.ring.data.db.CalendarLinkDao
import com.nobby.ring.data.db.CalendarLinkEntity
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

class FakeCalendarSource : CalendarSource {
    val events = linkedMapOf<Long, CalendarEntry>()
    /** Calendar each event lives in, like the provider's CALENDAR_ID. */
    val calendarOf = mutableMapOf<Long, Long>()
    private var nextId = 100L
    var calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0))
    /** Simulates a failed provider query (null cursor). */
    var listFails = false
    override suspend fun listWritable() = listAll()?.filter { it.writable } ?: emptyList()
    override suspend fun listAll(): List<DeviceCalendar>? = if (listFails) null else calendars
    override suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long {
        val id = nextId++
        events[id] = entry
        calendarOf[id] = calendarId
        return id
    }
    /** A user's own event: no NoBby marker. */
    fun addForeign(calendarId: Long, title: String = "Dentist"): Long {
        val id = nextId++
        events[id] = CalendarEntry("foreign-$id", title, LocalDateTime(2026, 11, 5, 9, 0), 30, "my notes")
        calendarOf[id] = calendarId
        return id
    }
    override suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone): Int {
        if (providerId !in events) return 0
        events[providerId] = entry
        return 1
    }
    // Like the provider: live row, NoBby marker in the description and the chosen calendar.
    override suspend fun isOwnEvent(providerId: Long, calendarId: Long): Boolean =
        events[providerId]?.description?.startsWith(CalendarEntry.MARKER_PREFIX) == true && calendarOf[providerId] == calendarId
    override suspend fun delete(providerId: Long): Boolean { calendarOf.remove(providerId); return events.remove(providerId) != null }
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

    @Test
    fun `a link to a provider event that is not ours is dropped and the event re-created`() = runTest {
        syncer.syncNow()
        val link = linkDao.allOnce().first()
        // The provider event vanished (or the id now belongs to someone else's event) and the schedule moved.
        source.delete(link.providerEventId)
        linkDao.upsert(listOf(CalendarLinkEntity(link.eventId, 999L, "2026-01-01T00:00", link.title)))
        assertEquals(1, syncer.syncNow().getOrThrow())
        val relinked = linkDao.allOnce().single { it.eventId == link.eventId }
        assertNotEquals(999L, relinked.providerEventId)
        assertTrue(relinked.providerEventId in source.events)
        assertEquals(4, source.events.size)
        assertEquals(4, linkDao.allOnce().size)
    }

    @Test
    fun `a stored calendar that no longer exists fails without writing and keeps lastSync and the links`() = runTest {
        syncer.syncNow().getOrThrow()
        val lastBefore = syncStore.lastSync.first()
        source.calendars = emptyList()
        val before = source.events.toMap()
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(8, 0)), LocalDateTime(2026, 11, 1, 8, 0), 2))

        val result = syncer.syncNow()

        assertTrue(result.exceptionOrNull() is CalendarUnavailableException)
        assertEquals(before, source.events.toMap())
        assertEquals(lastBefore, syncStore.lastSync.first())
        assertEquals(4, linkDao.allOnce().size)
        assertEquals(true, syncStore.enabled.first())
    }

    @Test
    fun `a calendar that is no longer in the writable list is treated as gone`() = runTest {
        source.calendars = listOf(DeviceCalendar(7, "Other", "x", 0))
        assertTrue(syncer.syncNow().exceptionOrNull() is CalendarUnavailableException)
        assertEquals(0, source.events.size)
        assertEquals(null, syncStore.lastSync.first())
    }

    @Test
    fun `picking another calendar after the loss recovers`() = runTest {
        syncer.syncNow().getOrThrow()
        source.calendars = listOf(DeviceCalendar(2, "Other", "x", 0))
        assertTrue(syncer.syncNow().isFailure)
        syncer.clearLinks() // what the settings screen does when the user picks
        syncStore.save(enabled = true, calendarId = 2)
        assertEquals(4, syncer.syncNow().getOrThrow())
        assertEquals(4, linkDao.allOnce().size)
    }

    @Test
    fun `a read-only calendar fails without writing and keeps links, and works again when writable`() = runTest {
        syncer.syncNow().getOrThrow()
        val cal = source.calendars.single()
        source.calendars = listOf(cal.copy(writable = false))
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(8, 0)), LocalDateTime(2026, 11, 1, 8, 0), 2))
        val lastBefore = syncStore.lastSync.first()
        val before = source.events.toMap()

        assertTrue(syncer.syncNow().exceptionOrNull() is CalendarUnavailableException)
        assertEquals(before, source.events.toMap())
        assertEquals(4, linkDao.allOnce().size)
        assertEquals(lastBefore, syncStore.lastSync.first())

        source.calendars = listOf(cal)
        syncer.syncNow().getOrThrow()
        assertEquals(4, source.events.size)
        assertEquals(4, linkDao.allOnce().size)
    }

    @Test
    fun `a failed calendar query neither writes nor clears links nor advances lastSync`() = runTest {
        syncer.syncNow().getOrThrow()
        val lastBefore = syncStore.lastSync.first()
        source.listFails = true
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(8, 0)), LocalDateTime(2026, 11, 1, 8, 0), 2))
        val before = source.events.toMap()

        val result = syncer.syncNow()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() !is CalendarUnavailableException)
        assertEquals(before, source.events.toMap())
        assertEquals(4, linkDao.allOnce().size)
        assertEquals(lastBefore, syncStore.lastSync.first())

        source.listFails = false
        syncer.syncNow().getOrThrow()
        assertEquals(4, source.events.size)
    }

    @Test
    fun `a foreign event survives an unavailable sync and a later sync with stale links`() = runTest {
        syncer.syncNow().getOrThrow()
        val foreign = source.addForeign(1)
        // A stale link whose provider id is now the user's event (id reuse).
        val link = linkDao.allOnce().first()
        linkDao.upsert(listOf(CalendarLinkEntity(link.eventId, foreign, "2026-01-01T00:00", link.title)))
        source.calendars = emptyList()
        assertTrue(syncer.syncNow().isFailure)
        source.calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0))

        syncer.syncNow().getOrThrow()

        assertEquals("Dentist", source.events[foreign]?.title)
        assertEquals("my notes", source.events[foreign]?.description)
        assertEquals(4, linkDao.allOnce().size)
    }

    @Test
    fun `a reused id with a different identity is gone - no writes, no lastSync, pick again recovers`() = runTest {
        syncer.syncNow().getOrThrow()
        val lastBefore = syncStore.lastSync.first()
        source.events.clear(); source.calendarOf.clear() // the old calendar and its events died with it
        source.calendars = listOf(DeviceCalendar(1, "Other", "someone@example.com", 0))

        val result = syncer.syncNow()

        assertTrue(result.exceptionOrNull() is CalendarUnavailableException)
        assertEquals(0, source.events.size)
        assertEquals(lastBefore, syncStore.lastSync.first())

        syncer.clearLinks()
        syncStore.save(enabled = true, calendarId = 1, identity = source.calendars.single().identity)
        assertEquals(4, syncer.syncNow().getOrThrow())
    }

    @Test
    fun `a recreated identical calendar with the same id keeps syncing`() = runTest {
        syncer.syncNow().getOrThrow()
        source.events.clear(); source.calendarOf.clear()
        source.calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0))

        // Same identity is not "gone"; the next schedule change re-creates what the old calendar took with it.
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(8, 0)), LocalDateTime(2026, 11, 1, 8, 0), 2))
        syncer.syncNow().getOrThrow()
        assertEquals(4, source.events.size)
        assertEquals(4, linkDao.allOnce().size)
    }

    @Test
    fun `a stored choice without identity captures it on the next successful sync, then guards`() = runTest {
        assertEquals(null, syncStore.calendarIdentity.first())
        syncer.syncNow().getOrThrow()
        assertEquals(source.calendars.single().identity, syncStore.calendarIdentity.first())

        source.calendars = listOf(DeviceCalendar(1, "Other", "x", 0))
        assertTrue(syncer.syncNow().exceptionOrNull() is CalendarUnavailableException)
    }

    @Test
    fun `worker maps an unavailable calendar to failure without retry`() {
        val unavailable = kotlin.Result.failure<Int>(CalendarUnavailableException(1))
        assertEquals(ListenableWorker.Result.failure(), CalendarSyncWorker.resultFor(unavailable, 0))
        assertEquals(ListenableWorker.Result.failure(), CalendarSyncWorker.resultFor(kotlin.Result.failure(SecurityException()), 0))
        assertEquals(ListenableWorker.Result.retry(), CalendarSyncWorker.resultFor(kotlin.Result.failure(IllegalStateException()), 0))
        assertEquals(ListenableWorker.Result.failure(), CalendarSyncWorker.resultFor(kotlin.Result.failure(IllegalStateException()), 3))
        assertEquals(ListenableWorker.Result.success(), CalendarSyncWorker.resultFor(kotlin.Result.success(0), 0))
    }

    @Test
    fun `worker gives up on the third run - attempt counts start at 0`() {
        val boom = kotlin.Result.failure<Int>(IllegalStateException())
        assertEquals(ListenableWorker.Result.retry(), CalendarSyncWorker.resultFor(boom, 0))
        assertEquals(ListenableWorker.Result.retry(), CalendarSyncWorker.resultFor(boom, 1))
        assertEquals(ListenableWorker.Result.failure(), CalendarSyncWorker.resultFor(boom, 2))
    }

    @Test
    fun `a sync id assigned after the choice does not make the calendar a different one`() = runTest {
        source.calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0, identity = "test@example.com|LOCAL|Test|"))
        syncer.syncNow().getOrThrow()
        assertEquals("test@example.com|LOCAL|Test|", syncStore.calendarIdentity.first())

        source.calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0, identity = "test@example.com|LOCAL|Test|abc123"))
        syncer.syncNow().getOrThrow()
        assertEquals("test@example.com|LOCAL|Test|abc123", syncStore.calendarIdentity.first())

        // Once a sync id is stored it is part of the identity again.
        source.calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0, identity = "test@example.com|LOCAL|Test|other"))
        assertTrue(syncer.syncNow().exceptionOrNull() is CalendarUnavailableException)
    }

    @Test
    fun `an empty stored sync id still tells another account apart`() = runTest {
        source.calendars = listOf(DeviceCalendar(1, "Test", "test@example.com", 0, identity = "test@example.com|LOCAL|Test|"))
        syncer.syncNow().getOrThrow()
        source.calendars = listOf(DeviceCalendar(1, "Test", "x@example.com", 0, identity = "x@example.com|LOCAL|Test|abc"))
        assertTrue(syncer.syncNow().exceptionOrNull() is CalendarUnavailableException)
    }
}
