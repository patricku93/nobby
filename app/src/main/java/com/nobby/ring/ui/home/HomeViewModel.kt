package com.nobby.ring.ui.home

import android.util.Log
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

data class HomeUiState(
    val state: ScheduleState? = null,
    val pendingConfirm: PendingConfirm? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ScheduleRepository,
    private val clock: NobbyClock,
    private val notifier: ScheduleChangeNotifier,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val exactAlarmsAllowed: Boolean get() = scheduler.canScheduleExact()

    private val pending = MutableStateFlow<PendingConfirm?>(null)

    private val committing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(repo.state, pending, committing) { s, p, c -> HomeUiState(s, p, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onDoneTapped() {
        if (committing.value) return
        val event = uiState.value.state?.nextEvent ?: return
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

    private fun delayBetween(from: LocalDateTime, to: LocalDateTime): Duration =
        to.toInstant(TimeZone.UTC) - from.toInstant(TimeZone.UTC)
}
