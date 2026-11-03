package com.nobby.ring.ui.onboarding

import androidx.lifecycle.ViewModel
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.SetupPlan
import com.nobby.ring.domain.engine.SetupStart
import com.nobby.ring.domain.engine.SetupStatus
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

/** Why the status step's date and time cannot be used (shown inline; Next stays off). */
enum class StatusIssue { IN_FUTURE, IN_PAST }

data class OnboardingState(
    val step: Int = 0,
    /** Step 1: where the user stands; null until answered. */
    val status: SetupStatus? = null,
    /** Step 1: when the ring went in / came out / will go in (the meaning follows [status]). */
    val statusDate: LocalDate,
    val statusTime: LocalTime = LocalTime(21, 0),
    /** True once the user picked a date or time, so a later answer change no longer replaces them with defaults. */
    val statusEdited: Boolean = false,
    val statusIssue: StatusIssue? = null,
    /** "Ring is out" was answered but the break is already over: the step now asks for the planned insertion. */
    val pauseOver: Boolean = false,
    val wearDays: Int = 21,
    val pauseDays: Int = 7,
    val timeOfDay: LocalTime = LocalTime(21, 0),
    val palette: Palette = Palette.BERRY,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val finished: Boolean = false,
    val saving: Boolean = false,
) {
    val statusAt: LocalDateTime get() = LocalDateTime(statusDate, statusTime)
    val canNext: Boolean get() = step != 0 || (status != null && statusIssue == null)
    val wearWarning: Boolean get() = wearDays > ScheduleConfig.SAFE_MAX_WEAR
    val pauseWarning: Boolean get() = pauseDays > ScheduleConfig.SAFE_MAX_PAUSE
    val method: Method get() = Method.ring(wearDays, pauseDays, timeOfDay)
    companion object { const val STEPS = 4 }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val configStore: MethodConfigStore,
    private val reminders: ReminderRepository,
    private val themeStore: ThemeStore,
    private val schedule: ScheduleRepository,
    private val notifier: ScheduleChangeNotifier,
    @ApplicationScope private val appScope: CoroutineScope,
    private val clock: NobbyClock,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState(statusDate = clock.now().date))
    val state: StateFlow<OnboardingState> = _state

    /** From the status step only with a usable answer; a break that is already over turns it into "not started". */
    fun next() {
        val s = _state.value
        if (!s.canNext) return
        if (s.step == 0 && planFor(s) == SetupPlan.PauseOver) {
            _state.update { pauseIsOver(it) }
            return
        }
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(OnboardingState.STEPS - 1)) }
    }

    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    fun setWear(days: Int) = _state.update { it.copy(wearDays = days.coerceIn(ScheduleConfig.MIN_WEAR, ScheduleConfig.MAX_WEAR)) }
    fun setPause(days: Int) = _state.update { it.copy(pauseDays = days.coerceIn(ScheduleConfig.MIN_PAUSE, ScheduleConfig.MAX_PAUSE)) }
    fun setTime(time: LocalTime) = _state.update { it.copy(timeOfDay = time) }
    fun setPalette(palette: Palette) = _state.update { it.copy(palette = palette) }
    fun setMode(mode: ThemeMode) = _state.update { it.copy(mode = mode) }

    fun setStatus(status: SetupStatus) = _state.update { s ->
        val picked = s.copy(status = status, pauseOver = false)
        checked(if (s.statusEdited) picked else withDefaults(picked))
    }

    fun setStatusDate(date: LocalDate) = _state.update { checked(it.copy(statusDate = date, statusEdited = true)) }
    fun setStatusTime(time: LocalTime) = _state.update { checked(it.copy(statusTime = time, statusEdited = true)) }

    /**
     * Saves the schedule computed from the status answer with the FINAL lengths. A "ring is out" start then confirms
     * that cycle's removal through ScheduleRepository, exactly like a confirmation in the app. If the answer no longer
     * fits (the break is over with the chosen pause, or time moved past the planned start), it goes back to step 1.
     */
    fun finish() {
        val s = _state.value
        if (s.saving || s.finished) return
        val plan = planFor(s)
        if (plan !is SetupPlan.Ready) {
            _state.update { if (plan == SetupPlan.PauseOver) pauseIsOver(it) else checked(it).copy(step = 0) }
            return
        }
        _state.update { it.copy(saving = true) }
        // App scope: the writes and the first re-planning must complete even if the screen goes away mid-save.
        appScope.launch {
            runCatching { save(s, plan) }
                .onSuccess { _state.update { it.copy(finished = true) } }
                .onFailure {
                    if (it is CancellationException) throw it
                    android.util.Log.e("NoBby", "onboarding save failed", it)
                    _state.update { it.copy(saving = false) }
                }
        }
    }

    private suspend fun save(s: OnboardingState, plan: SetupPlan.Ready) {
        configStore.save(plan.config)
        plan.removal?.let { schedule.confirm(it.eventId, it.confirmedAt, it.policy) }
        reminders.replaceAll(ReminderRule.defaults(s.timeOfDay))
        themeStore.save(ThemeSpec(palette = s.palette, mode = s.mode))
        configStore.setOnboardingDone()
        notifier.notifyChanged() // arm reminders, overdue alarm, widget and calendar for the new schedule
    }

    private fun planFor(s: OnboardingState): SetupPlan? =
        s.status?.let { SetupStart.plan(it, s.method, s.statusAt, clock.now()) }

    private fun checked(s: OnboardingState): OnboardingState {
        val issue = when (planFor(s)) {
            SetupPlan.InFuture -> StatusIssue.IN_FUTURE
            SetupPlan.InPast -> StatusIssue.IN_PAST
            else -> null
        }
        return s.copy(statusIssue = issue)
    }

    /** The break is over: ask for the planned insertion, as for "not started yet", with the notice shown. */
    private fun pauseIsOver(s: OnboardingState): OnboardingState =
        checked(withDefaults(s.copy(step = 0, status = SetupStatus.NOT_STARTED, statusEdited = false))).copy(pauseOver = true)

    /**
     * Routine time today, moved a day back for "in" / "out" when that is still ahead, or a day on for "not started"
     * when it has already passed, so the preset answer is always a valid one.
     */
    private fun withDefaults(s: OnboardingState): OnboardingState {
        val now = clock.now()
        val today = now.date.atTime(s.timeOfDay)
        val at = when (s.status) {
            SetupStatus.NOT_STARTED -> if (today < now) today.date.plus(1, DateTimeUnit.DAY).atTime(s.timeOfDay) else today
            else -> if (today > now) today.date.minus(1, DateTimeUnit.DAY).atTime(s.timeOfDay) else today
        }
        return s.copy(statusDate = at.date, statusTime = at.time)
    }
}
