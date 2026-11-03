package com.nobby.ring.ui.onboarding

import androidx.lifecycle.ViewModel
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import javax.inject.Inject

data class OnboardingState(
    val step: Int = 0,
    val wearDays: Int = 21,
    val pauseDays: Int = 7,
    val timeOfDay: LocalTime = LocalTime(21, 0),
    val anchorDate: LocalDate,
    val anchorTime: LocalTime = LocalTime(21, 0),
    val palette: Palette = Palette.BERRY,
    val finished: Boolean = false,
    val saving: Boolean = false,
) {
    val wearWarning: Boolean get() = wearDays > ScheduleConfig.SAFE_MAX_WEAR
    val pauseWarning: Boolean get() = pauseDays > ScheduleConfig.SAFE_MAX_PAUSE
    companion object { const val STEPS = 5 }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val configStore: MethodConfigStore,
    private val reminders: ReminderRepository,
    private val themeStore: ThemeStore,
    private val notifier: ScheduleChangeNotifier,
    @ApplicationScope private val appScope: CoroutineScope,
    clock: NobbyClock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(anchorDate = clock.now().date))
    val state: StateFlow<OnboardingState> = _state

    fun next() = _state.update { it.copy(step = (it.step + 1).coerceAtMost(OnboardingState.STEPS - 1)) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    fun setWear(days: Int) = _state.update { it.copy(wearDays = days.coerceIn(ScheduleConfig.MIN_WEAR, ScheduleConfig.MAX_WEAR)) }
    fun setPause(days: Int) = _state.update { it.copy(pauseDays = days.coerceIn(ScheduleConfig.MIN_PAUSE, ScheduleConfig.MAX_PAUSE)) }
    fun setTime(time: LocalTime) = _state.update { it.copy(timeOfDay = time, anchorTime = time) }
    fun setAnchorDate(date: LocalDate) = _state.update { it.copy(anchorDate = date) }
    fun setAnchorTime(time: LocalTime) = _state.update { it.copy(anchorTime = time) }
    fun setPalette(palette: Palette) = _state.update { it.copy(palette = palette) }

    fun finish() {
        val s = _state.value
        if (s.saving || s.finished) return
        _state.update { it.copy(saving = true) }
        // App scope: the writes and the first re-planning must complete even if the screen goes away mid-save.
        appScope.launch {
            runCatching {
                configStore.save(ScheduleConfig(Method.ring(s.wearDays, s.pauseDays, s.timeOfDay), LocalDateTime(s.anchorDate, s.anchorTime)))
                reminders.replaceAll(ReminderRule.defaults(s.timeOfDay))
                themeStore.save(ThemeSpec(palette = s.palette))
                configStore.setOnboardingDone()
                notifier.notifyChanged() // arm reminders, overdue alarm, widget and calendar for the new schedule
            }.onSuccess {
                _state.update { it.copy(finished = true) }
            }.onFailure {
                if (it is CancellationException) throw it
                android.util.Log.e("NoBby", "onboarding save failed", it)
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
