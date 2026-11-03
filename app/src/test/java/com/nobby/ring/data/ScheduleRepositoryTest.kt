package com.nobby.ring.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {
    private lateinit var db: NobbyDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: ScheduleRepository
    private val fixedNow = LocalDateTime(2026, 11, 22, 23, 0)
    private val clock = object : NobbyClock {
        override fun now() = fixedNow
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        dataStore = PreferenceDataStoreFactory.create { File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb") }
        repo = ScheduleRepository(MethodConfigStore(dataStore), db.eventLogDao(), clock)
    }

    @After fun tearDown() = db.close()

    @Test
    fun `state is null before onboarding`() = runTest {
        assertNull(repo.state.first())
    }

    @Test
    fun `saving config produces a state with overdue remove`() = runTest {
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        val state = repo.state.filterNotNull().first()
        assertEquals(EventKind.REMOVE, state.overdue!!.event.kind)
        assertEquals(0, state.daysUntilNext)
    }

    @Test
    fun `confirm clears overdue and persists the log`() = runTest {
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        val before = repo.state.filterNotNull().first()
        repo.confirm(before.nextEvent!!.id, fixedNow, ShiftPolicy.KEEP)
        val after = repo.state.filterNotNull().first()
        assertNull(after.overdue)
        assertEquals(EventKind.INSERT, after.nextEvent!!.kind)
        assertEquals(1, db.eventLogDao().all().first().size)
    }

    @Test
    fun `skipPause turns next remove into swap`() = runTest {
        repo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        repo.skipPause()
        assertEquals(EventKind.SWAP, repo.state.filterNotNull().first().nextEvent!!.kind)
    }

    @Test
    fun `confirm with unknown eventId is a no-op`() = runTest {
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
        repo.updateConfig(config)
        repo.confirm("does-not-exist", fixedNow, ShiftPolicy.KEEP)
        assertEquals(0, db.eventLogDao().allOnce().size)
        assertEquals(config, repo.state.filterNotNull().first().config)
    }

    @Test
    fun `confirm late remove with SHIFT moves the next insert and leaves one log`() = runTest {
        val lateClock = object : NobbyClock {
            override fun now() = LocalDateTime(2026, 11, 24, 21, 0)
            override val zone = TimeZone.UTC
        }
        val lateRepo = ScheduleRepository(MethodConfigStore(dataStore), db.eventLogDao(), lateClock)
        lateRepo.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        val before = lateRepo.state.filterNotNull().first()
        assertEquals(EventKind.REMOVE, before.nextEvent!!.kind)
        lateRepo.confirm(before.nextEvent!!.id, LocalDateTime(2026, 11, 24, 21, 0), ShiftPolicy.SHIFT)
        val after = lateRepo.state.filterNotNull().first()
        assertEquals(1, db.eventLogDao().allOnce().size)
        assertEquals(EventKind.INSERT, after.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 12, 1, 21, 0), after.nextEvent!!.plannedAt)
        assertNull(after.overdue)
    }
    @Test
    fun `apply with an older anchor after a confirmed removal keeps the ring in while that remove is overdue (D9)`() = runTest {
        val method = Method.ring(21, 7, LocalTime(21, 0))
        repo.updateConfig(ScheduleConfig(method, LocalDateTime(2026, 11, 1, 21, 0)))
        val before = repo.state.filterNotNull().first()
        repo.confirm(before.nextEvent!!.id, fixedNow, ShiftPolicy.KEEP) // REMOVE Nov 22 confirmed
        val pause = repo.state.filterNotNull().first()
        assertEquals(PhaseKind.PAUSE, pause.position.phase)
        repo.updateConfig(pause.config.copy(anchor = LocalDateTime(2026, 9, 20, 21, 0))) // Settings > Cycle & schedule > Apply
        val after = repo.state.filterNotNull().first()
        assertEquals(EventKind.REMOVE, after.overdue!!.event.kind)
        assertEquals(PhaseKind.WEAR, after.position.phase)
        assertEquals(true, after.position.ringIn)
    }

    // --- I1: Settings > Cycle & schedule > Apply after the removal was confirmed -------------------------------------

    private val ring21 = Method.ring(21, 7, LocalTime(21, 0))
    private val nov1 = LocalDateTime(2026, 11, 1, 21, 0)

    private fun repoAt(now: LocalDateTime) = ScheduleRepository(MethodConfigStore(dataStore), db.eventLogDao(), object : NobbyClock {
        override fun now() = now
        override val zone = TimeZone.UTC
    })

    /** Ring in since Nov 1 21:00, removal (planned Nov 22 21:00) confirmed at [confirmedAt] through the real confirm path. */
    private suspend fun ScheduleRepository.confirmedRemoval(confirmedAt: LocalDateTime = fixedNow, policy: ShiftPolicy = ShiftPolicy.KEEP) =
        run {
            updateConfig(ScheduleConfig(ring21, nov1))
            val wear = state.filterNotNull().first()
            assertEquals(EventKind.REMOVE, wear.nextEvent!!.kind)
            confirm(wear.nextEvent!!.id, confirmedAt, policy)
            val pause = state.filterNotNull().first()
            assertEquals(PhaseKind.PAUSE, pause.position.phase)
            pause
        }

    private suspend fun ScheduleRepository.assertPauseUntil(insertAt: LocalDateTime) {
        val s = state.filterNotNull().first()
        assertEquals(PhaseKind.PAUSE, s.position.phase)
        assertEquals(false, s.position.ringIn)
        assertEquals(EventKind.INSERT, s.nextEvent!!.kind)
        assertEquals(insertAt, s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
        assertEquals("one removal in the history", 1, db.eventLogDao().allOnce().size)
    }

    @Test
    fun `changing the time of day in the pause keeps the confirmed removal (I1)`() = runTest {
        val pause = repo.confirmedRemoval()
        repo.updateConfig(pause.config.copy(method = Method.ring(21, 7, LocalTime(20, 0))))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 29, 20, 0))
    }

    @Test
    fun `one wear day less in the pause keeps the confirmed removal and shows no overdue (I1)`() = runTest {
        val pause = repo.confirmedRemoval()
        repo.updateConfig(pause.config.copy(method = Method.ring(20, 7, LocalTime(21, 0))))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 28, 21, 0))
    }

    @Test
    fun `one wear day more in the pause keeps the confirmed removal and the break within the pause (I1, I-A)`() = runTest {
        val pause = repo.confirmedRemoval() // out Nov 22 23:00
        repo.updateConfig(pause.config.copy(method = Method.ring(22, 7, LocalTime(21, 0))))
        // Nov 30 21:00 would be a 7 d 22 h break; the latest planned time within 7 days of the removal is Nov 29 21:00.
        repo.assertPauseUntil(LocalDateTime(2026, 11, 29, 21, 0))
    }

    @Test
    fun `one pause day more in the pause plans no break longer than the new pause (I-A)`() = runTest {
        val pause = repo.confirmedRemoval()
        repo.updateConfig(pause.config.copy(method = Method.ring(21, 8, LocalTime(21, 0))))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 30, 21, 0)) // 7 d 22 h <= 8 d
    }

    @Test
    fun `a start moved to just before the removal keeps the break within the pause (I-A)`() = runTest {
        val pause = repo.confirmedRemoval()
        repo.updateConfig(pause.config.copy(anchor = LocalDateTime(2026, 11, 20, 21, 0)))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 29, 21, 0)) // not Dec 18 (a 26-day break)
    }

    @Test
    fun `a new ring put in on the removal's day stays in wear with its removal planned (I-B)`() = runTest {
        val pause = repo.confirmedRemoval() // out Nov 22 23:00
        repo.updateConfig(pause.config.copy(anchor = LocalDateTime(2026, 11, 22, 21, 0)))
        val s = repo.state.filterNotNull().first()
        assertEquals(PhaseKind.WEAR, s.position.phase)
        assertEquals(true, s.position.ringIn)
        assertEquals(EventKind.REMOVE, s.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 12, 13, 21, 0), s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
    }

    @Test
    fun `apply removes a stale duplicate of the carried removal (M-A)`() = runTest {
        val pause = repo.confirmedRemoval()
        val row = db.eventLogDao().allOnce().single()
        db.eventLogDao().upsert(listOf(row.copy(eventId = "REMOVE@stale"))) // left over by a crash between save and delete
        repo.updateConfig(pause.config.copy(method = Method.ring(21, 7, LocalTime(20, 0))))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 29, 20, 0))
    }

    @Test
    fun `fewer pause days in the pause keep the confirmed removal (I1)`() = runTest {
        val pause = repo.confirmedRemoval()
        repo.updateConfig(pause.config.copy(method = Method.ring(21, 5, LocalTime(21, 0))))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 27, 21, 0))
    }

    @Test
    fun `a removal confirmed a day late with SHIFT survives a new time of day (I1)`() = runTest {
        val late = repoAt(LocalDateTime(2026, 11, 24, 12, 0))
        val pause = late.confirmedRemoval(LocalDateTime(2026, 11, 23, 22, 0), ShiftPolicy.SHIFT) // anchor -> Nov 2
        late.updateConfig(pause.config.copy(method = Method.ring(21, 7, LocalTime(8, 30))))
        late.assertPauseUntil(LocalDateTime(2026, 11, 30, 8, 30))
    }

    @Test
    fun `correcting the start date by a day in the pause keeps the removal of the same cycle (I1)`() = runTest {
        val pause = repo.confirmedRemoval()
        repo.updateConfig(pause.config.copy(anchor = LocalDateTime(2026, 10, 31, 21, 0)))
        repo.assertPauseUntil(LocalDateTime(2026, 11, 28, 21, 0))
        repo.updateConfig(pause.config.copy(anchor = LocalDateTime(2026, 11, 3, 21, 0)))
        // Dec 1 21:00 would be an 8 d 22 h break; capped like an early removal (I-A).
        repo.assertPauseUntil(LocalDateTime(2026, 11, 29, 21, 0))
    }

    @Test
    fun `a new start date after the removal is a new ring and stays in wear (I1)`() = runTest {
        val later = repoAt(LocalDateTime(2026, 11, 26, 12, 0))
        val pause = later.confirmedRemoval()
        later.updateConfig(pause.config.copy(anchor = LocalDateTime(2026, 11, 26, 10, 0)))
        val s = later.state.filterNotNull().first()
        assertEquals(PhaseKind.WEAR, s.position.phase)
        assertEquals(EventKind.REMOVE, s.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 12, 17, 21, 0), s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
    }

    @Test
    fun `changing the time of day during wear leaves the ring in (I1)`() = runTest {
        val wear = repoAt(LocalDateTime(2026, 11, 10, 12, 0))
        wear.updateConfig(ScheduleConfig(ring21, nov1))
        wear.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(20, 0)), nov1))
        val s = wear.state.filterNotNull().first()
        assertEquals(PhaseKind.WEAR, s.position.phase)
        assertEquals(true, s.position.ringIn)
        assertEquals(EventKind.REMOVE, s.nextEvent!!.kind)
        assertEquals(LocalDateTime(2026, 11, 22, 20, 0), s.nextEvent!!.plannedAt)
        assertNull(s.overdue)
        assertEquals(0, db.eventLogDao().allOnce().size)
    }

    @Test
    fun `startWith saves the setup's removal and config together`() = runTest {
        val removedAt = LocalDateTime(2026, 11, 20, 21, 0)
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 10, 30, 21, 0))
        val removeId = com.nobby.ring.domain.engine.CycleCalculator.currentRemoveId(config)
        repo.startWith(config, listOf(com.nobby.ring.domain.model.EventLog(removeId, EventKind.REMOVE, removedAt, removedAt)))
        val state = repo.state.filterNotNull().first()
        assertEquals(PhaseKind.PAUSE, state.position.phase)
        assertEquals(EventKind.INSERT, state.nextEvent!!.kind)
        assertNull(state.overdue)
    }
}
