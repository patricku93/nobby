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
}
