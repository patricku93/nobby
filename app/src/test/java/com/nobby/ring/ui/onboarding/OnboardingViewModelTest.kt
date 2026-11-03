package com.nobby.ring.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.notifications.ScheduleChangeListener
import com.nobby.ring.notifications.ScheduleChangeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var configStore: MethodConfigStore
    private lateinit var themeStore: ThemeStore
    private lateinit var reminders: ReminderRepository
    private lateinit var db: NobbyDatabase
    @Volatile private var changes = 0
    private val notifier = ScheduleChangeNotifier(setOf(object : ScheduleChangeListener {
        override suspend fun onScheduleChanged() { changes++ }
    }))
    private val clock = object : NobbyClock {
        override fun now() = LocalDateTime(2026, 11, 5, 10, 0)
        override val zone = TimeZone.UTC
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "onb-${System.nanoTime()}.preferences_pb") }
        configStore = MethodConfigStore(ds)
        themeStore = ThemeStore(ds)
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        reminders = ReminderRepository(db.reminderRuleDao())
    }

    @After fun tearDown() { Dispatchers.resetMain(); db.close() }

    @Test
    fun `finish saves config, default reminders, theme and marks onboarding done`() = runTest(dispatcher) {
        val vm = OnboardingViewModel(configStore, reminders, themeStore, notifier, CoroutineScope(dispatcher), clock)
        vm.setWear(21); vm.setPause(7); vm.setTime(LocalTime(21, 0))
        vm.setAnchorDate(LocalDate(2026, 11, 1)); vm.setAnchorTime(LocalTime(21, 0))
        vm.setPalette(Palette.MATCHA)
        vm.setMode(ThemeMode.DARK)
        vm.finish()
        dispatcher.scheduler.advanceUntilIdle()
        vm.state.first { it.finished } // DataStore writes complete on a real IO thread; wait for completion

        val config = configStore.config.first()!!
        assertEquals(LocalDateTime(2026, 11, 1, 21, 0), config.anchor)
        assertEquals(21, config.method.wearDays)
        assertTrue(configStore.onboardingDone.first())
        assertEquals(2, reminders.rules.first().size)
        assertEquals(Palette.MATCHA, themeStore.theme.first().palette)
        assertEquals(ThemeMode.DARK, themeStore.theme.first().mode)
        assertTrue(vm.state.value.finished)
        assertEquals(1, changes) // the schedule is armed (reminders, widget, calendar) right after onboarding
    }

    @Test
    fun `onboarding has four steps`() {
        val vm = OnboardingViewModel(configStore, reminders, themeStore, notifier, CoroutineScope(dispatcher), clock)
        repeat(6) { vm.next() }
        assertEquals(3, vm.state.value.step)
        assertEquals(4, OnboardingState.STEPS)
    }

    @Test
    fun `warnings appear above safety thresholds`() {
        val vm = OnboardingViewModel(configStore, reminders, themeStore, notifier, CoroutineScope(dispatcher), clock)
        vm.setWear(30); vm.setPause(9)
        assertTrue(vm.state.value.wearWarning)
        assertTrue(vm.state.value.pauseWarning)
    }
}
