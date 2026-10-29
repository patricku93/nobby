package com.nobby.ring.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.calendar.CalendarSource
import com.nobby.ring.data.calendar.CalendarSyncer
import com.nobby.ring.data.calendar.DeviceCalendar
import com.nobby.ring.data.calendar.FoundEvent
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.domain.NobbyClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import javax.inject.Inject

data class CalendarSettingsUiState(
    val enabled: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val selectedId: Long? = null,
    val lastSync: LocalDateTime? = null,
    val permissionGranted: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val results: List<FoundEvent> = emptyList(),
    val selectedResults: Set<Long> = emptySet(),
)

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: CalendarSource,
    private val syncer: CalendarSyncer,
    private val store: CalendarSyncStore,
    private val clock: NobbyClock,
) : ViewModel() {
    private val local = MutableStateFlow(
        CalendarSettingsUiState(from = clock.now().date, to = LocalDate(clock.now().year + 1, clock.now().monthNumber, 1), permissionGranted = hasPermission()),
    )

    val uiState: StateFlow<CalendarSettingsUiState> = combine(local, store.enabled, store.calendarId, store.lastSync) { l, enabled, id, last ->
        l.copy(enabled = enabled, selectedId = id, lastSync = last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun refreshPermission() {
        val granted = hasPermission()
        local.update { it.copy(permissionGranted = granted) }
        if (granted) viewModelScope.launch {
            runCatching { source.listWritable() }
                .onSuccess { list -> local.update { it.copy(calendars = list) } }
                .onFailure { e -> local.update { it.copy(error = e.message) } }
        }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        if (!enabled) {
            syncer.disconnect()
        } else {
            store.save(true, uiState.value.selectedId ?: uiState.value.calendars.firstOrNull()?.id)
            syncNow().join()
        }
    }

    fun select(id: Long) = viewModelScope.launch {
        val previous = uiState.value.selectedId
        // Events already written to the previous calendar must be removed before switching.
        if (previous != null && previous != id) syncer.disconnect()
        store.save(true, id)
        syncNow().join()
    }

    fun syncNow() = viewModelScope.launch {
        syncer.syncNow()
            .onSuccess { local.update { it.copy(error = null) } }
            .onFailure { e -> local.update { it.copy(error = e.message) } }
    }

    fun setQuery(q: String) = local.update { it.copy(query = q) }
    fun setFrom(d: LocalDate) = local.update { it.copy(from = d) }
    fun setTo(d: LocalDate) = local.update { it.copy(to = d) }

    fun search() = viewModelScope.launch {
        val s = uiState.value
        val id = s.selectedId ?: return@launch
        val regex = runCatching { Regex(s.query, RegexOption.IGNORE_CASE) }.getOrElse { Regex(Regex.escape(s.query), RegexOption.IGNORE_CASE) }
        val from = s.from.atStartOfDayIn(clock.zone).toEpochMilliseconds()
        val to = s.to.atTime(23, 59).toInstant(clock.zone).toEpochMilliseconds()
        runCatching { source.search(id, regex, from, to) }
            .onSuccess { r -> local.update { it.copy(results = r, selectedResults = r.map { f -> f.id }.toSet(), error = null) } }
            .onFailure { e -> local.update { it.copy(error = e.message) } }
    }

    fun toggleSelection(id: Long) = local.update { it.copy(selectedResults = if (id in it.selectedResults) it.selectedResults - id else it.selectedResults + id) }

    fun deleteSelected() = viewModelScope.launch {
        val ids = uiState.value.selectedResults
        ids.forEach { runCatching { source.delete(it) } }
        local.update { it.copy(results = it.results.filter { r -> r.id !in ids }, selectedResults = emptySet()) }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
