package com.nobby.ring.ui.home

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.notifications.ReminderScheduler
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration

data class PendingConfirm(val event: ScheduledEvent, val confirmedAt: LocalDateTime, val delay: Duration)

/** True when the next event is due today or overdue, i.e. it may be confirmed. */
val ScheduleState.isDue: Boolean get() = daysUntilNext?.let { it <= 0 } == true

data class HomeUiState(
    val state: ScheduleState? = null,
    val pendingConfirm: PendingConfirm? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ScheduleRepository,
    private val clock: NobbyClock,
    private val notifier: ScheduleChangeNotifier,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private val _exactAlarmsAllowed = MutableStateFlow(scheduler.canScheduleExact())
    /** Refreshed by [refreshPermissions] on every resume: the user may grant the permission in system settings. */
    val exactAlarmsAllowed: StateFlow<Boolean> = _exactAlarmsAllowed

    private val _notificationsEnabled = MutableStateFlow(notificationsOn())
    /** False when POST_NOTIFICATIONS is denied (API 33+) or notifications are switched off for the app. */
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled

    private val pending = MutableStateFlow<PendingConfirm?>(null)

    private val committing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(repo.state, pending, committing) { s, p, c -> HomeUiState(s, p, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refreshPermissions() {
        _exactAlarmsAllowed.value = scheduler.canScheduleExact()
        _notificationsEnabled.value = notificationsOn()
    }

    fun onDoneTapped() {
        if (committing.value) return
        val state = uiState.value.state ?: return
        // Only the event that is due today (or overdue) can be confirmed; future events are not "done" yet.
        if (!state.isDue) return
        val event = state.nextEvent ?: return
        val now = clock.now()
        if (ConfirmAction.needsPolicyChoice(event, now)) {
            pending.value = PendingConfirm(event, now, delayBetween(event.plannedAt, now))
        } else {
            commit(event, now, ShiftPolicy.KEEP)
        }
    }

    fun onConfirmTime(time: LocalDateTime) = pending.update { p -> p?.copy(confirmedAt = time, delay = delayBetween(p.event.plannedAt, time)) }

    fun onPolicyChosen(policy: ShiftPolicy) {
        if (committing.value) return
        val p = pending.value ?: return
        pending.value = null
        commit(p.event, p.confirmedAt, policy)
    }

    fun dismissConfirm() { pending.value = null }

    fun toggleSkipPause() = runExclusive("toggle skip pause") {
        val config = repo.currentState()?.config ?: return@runExclusive
        if (config.skipPauseOnce) repo.cancelSkipPause() else repo.skipPause()
    }

    private fun commit(event: ScheduledEvent, at: LocalDateTime, policy: ShiftPolicy) =
        runExclusive("confirm event") { repo.confirm(event.id, at, policy) }

    private fun runExclusive(label: String, block: suspend () -> Unit) {
        if (!committing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatching {
                    block()
                    notifier.notifyChanged()
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    Log.e("NoBby", "$label failed", e)
                }
            } finally {
                committing.value = false
            }
        }
    }

    private fun notificationsOn(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun delayBetween(from: LocalDateTime, to: LocalDateTime): Duration =
        to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)
}
