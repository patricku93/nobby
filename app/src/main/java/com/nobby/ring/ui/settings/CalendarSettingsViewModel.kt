package com.nobby.ring.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.R
import com.nobby.ring.data.calendar.CalendarSource
import com.nobby.ring.data.calendar.CalendarUnavailableException
import com.nobby.ring.data.calendar.CalendarSyncer
import com.nobby.ring.data.calendar.DeviceCalendar
import com.nobby.ring.data.calendar.FoundEvent
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.domain.NobbyClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    val selectedIdentity: String? = null,
    val lastSync: LocalDateTime? = null,
    val permissionGranted: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val from: LocalDate,
    val to: LocalDate,
    val results: List<FoundEvent> = emptyList(),
    val selectedResults: Set<Long> = emptySet(),
    val calendarsLoaded: Boolean = false,
) {
    /** Sync is on, but no calendar is chosen yet: nothing is written until one is picked. */
    val needsChoice: Boolean get() = enabled && selectedId == null
    /** The stored calendar vanished from the provider (deleted outside the app, account removed). */
    val selectedMissing: Boolean
        get() = enabled && calendarsLoaded && selectedId != null &&
            calendars.none { it.id == selectedId && (selectedIdentity == null || it.identity == selectedIdentity) }
}

@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: CalendarSource,
    private val syncer: CalendarSyncer,
    private val store: CalendarSyncStore,
    private val clock: NobbyClock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val local = MutableStateFlow(
        CalendarSettingsUiState(from = clock.now().date, to = LocalDate(clock.now().year + 1, clock.now().monthNumber, 1), permissionGranted = hasPermission()),
    )

    val uiState: StateFlow<CalendarSettingsUiState> = combine(local, store.enabled, store.calendarId, store.lastSync, store.calendarIdentity) { l, enabled, id, last, identity ->
        l.copy(enabled = enabled, selectedId = id, selectedIdentity = identity, lastSync = last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun refreshPermission() {
        val granted = hasPermission()
        local.update { it.copy(permissionGranted = granted) }
        if (granted) viewModelScope.launch { loadCalendars() }
    }

    private suspend fun loadCalendars() {
        runCatching { source.listWritable() }
            .onSuccess { list -> local.update { it.copy(calendars = list, calendarsLoaded = true) } }
            .onFailure { e ->
                if (e is CancellationException) throw e
                Log.e("NoBby", "Failed to list writable calendars", e)
                local.update { it.copy(error = e.message) }
            }
    }

    // Provider writes run on the application scope so leaving the screen cannot stop them half-way.
    fun setEnabled(enabled: Boolean) = appScope.launch {
        guarded("Calendar enable/disable failed") {
            if (!enabled) {
                syncer.disconnect()
            } else {
                loadCalendars()
                // Auto-select only when there is no ambiguity; with several calendars the user must pick one first.
                val kept = uiState.value.selectedId
                val auto = if (kept == null) local.value.calendars.singleOrNull() else null
                val id = kept ?: auto?.id
                store.save(true, id, auto?.identity)
                if (id != null) runSync()
            }
        }
    }

    fun select(id: Long) = appScope.launch {
        guarded("Calendar selection failed") {
            val state = uiState.value
            val picked = local.value.calendars.firstOrNull { it.id == id }
            val sameChoice = state.selectedId == id && (state.selectedIdentity == null || state.selectedIdentity == picked?.identity)
            // Tapping the calendar that is already in use (and still there) changes nothing.
            if (sameChoice && state.selectedIdentity != null && picked != null) return@guarded
            // Events already written to the previous calendar (or to one since replaced under the same id) must be
            // removed before switching; the store stays enabled.
            if (state.selectedId != null && !sameChoice) syncer.clearLinks()
            store.save(true, id, picked?.identity)
            runSync()
        }
    }

    fun syncNow() = viewModelScope.launch { runSync() }

    private suspend fun runSync() {
        syncer.syncNow()
            .onSuccess { local.update { it.copy(error = null) } }
            .onFailure { e ->
                // A vanished calendar is shown through selectedMissing (live and localised); it needs no stored error.
                if (e !is CalendarUnavailableException) Log.e("NoBby", "Calendar sync failed", e)
                local.update { it.copy(error = if (e is CalendarUnavailableException) null else e.message) }
                loadCalendars()
            }
    }

    private suspend fun guarded(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NoBby", label, e)
            local.update { it.copy(error = e.message) }
            loadCalendars()
        }
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
            .onSuccess { r -> local.update { it.copy(results = r, selectedResults = emptySet(), error = null) } }
            .onFailure { e ->
                Log.e("NoBby", "Calendar search failed", e)
                local.update { it.copy(error = e.message) }
            }
    }

    fun toggleSelection(id: Long) = local.update { it.copy(selectedResults = if (id in it.selectedResults) it.selectedResults - id else it.selectedResults + id) }

    fun toggleSelectAll() = local.update {
        val all = it.results.map { f -> f.id }.toSet()
        it.copy(selectedResults = if (it.selectedResults == all) emptySet() else all)
    }

    fun deleteSelected() = appScope.launch {
        val ids = uiState.value.selectedResults
        val succeeded = ids.filter { id ->
            try {
                source.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NoBby", "Failed to delete calendar event $id", e)
                false
            }
        }.toSet()
        // Links to events removed outside the sync would otherwise go stale.
        runCatching { syncer.forget(succeeded) }.onFailure { Log.e("NoBby", "Failed to forget deleted calendar links", it) }
        val failed = ids - succeeded
        local.update {
            it.copy(
                results = it.results.filter { r -> r.id !in succeeded },
                selectedResults = failed,
                error = if (failed.isEmpty()) null else context.getString(R.string.cleanup_delete_failed),
            )
        }
    }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}
