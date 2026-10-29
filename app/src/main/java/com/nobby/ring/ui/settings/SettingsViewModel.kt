package com.nobby.ring.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.OverdueConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.engine.ScheduleEngine
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
) {
    fun toConfig(): ScheduleConfig = ScheduleConfig(Method.ring(wearDays, pauseDays, timeOfDay), anchor, horizonMonths)
    val preview: List<ScheduledEvent> get() = ScheduleEngine.generate(toConfig()).take(4)
    val wearWarning: Boolean get() = wearDays > ScheduleConfig.SAFE_MAX_WEAR
    val pauseWarning: Boolean get() = pauseDays > ScheduleConfig.SAFE_MAX_PAUSE

    companion object {
        fun from(config: ScheduleConfig) = ScheduleDraft(config.method.wearDays, config.method.pauseDays, config.method.timeOfDay, config.anchor, config.horizonMonths)
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
) : ViewModel() {
    val schedule: StateFlow<ScheduleState?> = scheduleRepo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val theme: StateFlow<ThemeSpec> = themeStore.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSpec())
    val widget: StateFlow<WidgetConfig> = widgetStore.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetConfig())
    val rules: StateFlow<List<ReminderRule>> = reminders.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val overdue: StateFlow<OverdueConfig> = overdueStore.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverdueConfig())

    private val _draft = MutableStateFlow<ScheduleDraft?>(null)
    val draft: StateFlow<ScheduleDraft?> = _draft

    fun setTheme(theme: ThemeSpec) = viewModelScope.launch { themeStore.save(theme); notifier.notifyChanged() }
    fun setWidget(config: WidgetConfig) = viewModelScope.launch { widgetStore.save(config); notifier.notifyChanged() }
    fun setOverdue(config: OverdueConfig) = viewModelScope.launch { overdueStore.save(config); notifier.notifyChanged() }
    fun saveRule(rule: ReminderRule) = viewModelScope.launch { reminders.upsert(rule); notifier.notifyChanged() }
    fun deleteRule(id: Long) = viewModelScope.launch { reminders.delete(id); notifier.notifyChanged() }

    fun startScheduleDraft() = viewModelScope.launch {
        val config = scheduleRepo.currentState()?.config ?: return@launch
        _draft.value = ScheduleDraft.from(config)
    }

    fun updateDraft(transform: (ScheduleDraft) -> ScheduleDraft) = _draft.update { it?.let(transform) }

    fun applyDraft() = viewModelScope.launch {
        val d = _draft.value ?: return@launch
        try {
            scheduleRepo.updateConfig(d.toConfig())
            _draft.value = null
            notifier.notifyChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NoBby", "applying schedule draft failed", e)
        }
    }

    fun discardDraft() { _draft.value = null }
}
