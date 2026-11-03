package com.nobby.ring.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.notifications.ScheduleChangeListener
import com.nobby.ring.notifications.ScheduleChangeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: SettingsViewModel
    private lateinit var configStore: MethodConfigStore
    @Volatile private var changes = 0

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "set-${System.nanoTime()}.preferences_pb") }
        val clock = object : NobbyClock { override fun now() = LocalDateTime(2026, 11, 5, 12, 0); override val zone = TimeZone.UTC }
        configStore = MethodConfigStore(ds)
        val schedule = ScheduleRepository(configStore, db.eventLogDao(), clock)
        schedule.updateConfig(ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0)))
        vm = SettingsViewModel(
            schedule, ReminderRepository(db.reminderRuleDao()), ThemeStore(ds), WidgetConfigStore(ds), OverdueConfigStore(ds),
            ScheduleChangeNotifier(setOf(object : ScheduleChangeListener { override suspend fun onScheduleChanged() { changes++ } })),
            CoroutineScope(dispatcher),
        )
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `draft preview reflects a one-week shift and apply persists it`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()
        vm.startScheduleDraft()
        vm.draft.first { it != null }
        vm.updateDraft { it.copy(anchor = LocalDateTime(2026, 11, 8, 21, 0)) }
        assertEquals(LocalDateTime(2026, 11, 29, 21, 0), vm.draft.value!!.preview[0].plannedAt)
        vm.applyDraft()
        val expected = LocalDateTime(2026, 11, 8, 21, 0)
        assertEquals(expected, configStore.config.first { it?.anchor == expected }!!.anchor)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (changes == 0) delay(10) } }
        assertEquals(1, changes)
    }

    @Test
    fun `apply keeps a pending skip-pause (M5)`() = runTest(dispatcher) {
        dispatcher.scheduler.advanceUntilIdle()
        configStore.save(configStore.config.first()!!.copy(skipPauseOnce = true))
        vm.startScheduleDraft()
        vm.draft.first { it != null }
        vm.updateDraft { it.copy(timeOfDay = LocalTime(20, 0)) }
        vm.applyDraft()
        val saved = configStore.config.first { it?.method?.timeOfDay == LocalTime(20, 0) }!!
        assertEquals(true, saved.skipPauseOnce)
    }
}
