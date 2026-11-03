package com.nobby.ring.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.domain.engine.ConfigChange
import com.nobby.ring.domain.engine.ScheduleEngine
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.OverdueConfig
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.notifications.ScheduleChangeNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import javax.inject.Inject

data class ScheduleDraft(
    val wearDays: Int,
    val pauseDays: Int,
    val timeOfDay: LocalTime,
    val anchor: LocalDateTime,
    val horizonMonths: Int,
    /** Not edited on this screen; carried through so Apply does not cancel a pending skip-pause. */
    val skipPauseOnce: Boolean = false,
    /** Not edited here either: a schedule waiting for its first ring keeps waiting after Apply. */
    val firstInsertPending: Boolean = false,
    /** The saved schedule and log the draft started from, so the preview shows what Apply saves (carried removal). */
    val base: ScheduleConfig? = null,
    val logs: List<EventLog> = emptyList(),
) {
    fun toConfig(): ScheduleConfig = ScheduleConfig(Method.ring(wearDays, pauseDays, timeOfDay), anchor, horizonMonths, skipPauseOnce, firstInsertPending)
    val preview: List<ScheduledEvent> get() = ScheduleEngine.generate(ConfigChange.resolve(base, toConfig(), logs)).take(4)
    val wearWarning: Boolean get() = wearDays > ScheduleConfig.SAFE_MAX_WEAR
    val pauseWarning: Boolean get() = pauseDays > ScheduleConfig.SAFE_MAX_PAUSE

    companion object {
        fun from(config: ScheduleConfig, logs: List<EventLog> = emptyList()) = ScheduleDraft(
            config.method.wearDays, config.method.pauseDays, config.method.timeOfDay, config.anchor, config.horizonMonths, config.skipPauseOnce,
            config.firstInsertPending, base = config, logs = logs,
        )
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
    private val reminders: ReminderRepository,
    private val themeStore: ThemeStore,
    private val widgetStore: WidgetConfigStore,
    private val overdueStore: OverdueConfigStore,
    private val notifier: ScheduleChangeNotifier,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val schedule: StateFlow<ScheduleState?> = scheduleRepo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val theme: StateFlow<ThemeSpec> = themeStore.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSpec())
    val widget: StateFlow<WidgetConfig> = widgetStore.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetConfig())
    val rules: StateFlow<List<ReminderRule>> = reminders.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val overdue: StateFlow<OverdueConfig> = overdueStore.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverdueConfig())

    private val _draft = MutableStateFlow<ScheduleDraft?>(null)
    val draft: StateFlow<ScheduleDraft?> = _draft

    fun setTheme(theme: ThemeSpec) = persist("saving theme") { themeStore.save(theme) }
    fun setWidget(config: WidgetConfig) = persist("saving widget config") { widgetStore.save(config) }
    fun setOverdue(config: OverdueConfig) = persist("saving overdue config") { overdueStore.save(config) }
    fun saveRule(rule: ReminderRule) = persist("saving reminder rule") { reminders.upsert(rule) }
    fun deleteRule(id: Long) = persist("deleting reminder rule") { reminders.delete(id) }

    fun startScheduleDraft() = viewModelScope.launch {
        val state = scheduleRepo.currentState() ?: return@launch
        _draft.value = ScheduleDraft.from(state.config, state.logs)
    }

    fun updateDraft(transform: (ScheduleDraft) -> ScheduleDraft) = _draft.update { it?.let(transform) }

    fun applyDraft(): Job? {
        val d = _draft.value ?: return null
        return persist("applying schedule draft") {
            scheduleRepo.updateConfig(d.toConfig())
            _draft.value = null
        }
    }

    fun discardDraft() { _draft.value = null }

    /**
     * Runs [write] and then re-plans on the application scope, so leaving the screen right after tapping
     * cannot cancel a write half-way or skip the re-planning.
     */
    private fun persist(label: String, write: suspend () -> Unit): Job = appScope.launch {
        try {
            write()
            notifier.notifyChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NoBby", "$label failed", e)
        }
    }
}
