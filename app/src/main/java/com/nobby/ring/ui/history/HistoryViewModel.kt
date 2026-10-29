package com.nobby.ring.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration

data class HistoryEntry(val kind: EventKind, val plannedAt: LocalDateTime, val confirmedAt: LocalDateTime, val delay: Duration)

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: ScheduleRepository) : ViewModel() {
    val entries: StateFlow<List<HistoryEntry>> = repo.state.map { s ->
        (s?.logs ?: emptyList())
            .sortedByDescending { it.confirmedAt }
            // Both values are wall-clock local times; UTC is used only to get a DST-independent difference.
            .map { HistoryEntry(it.kind, it.plannedAt, it.confirmedAt, it.confirmedAt.toInstant(TimeZone.UTC) - it.plannedAt.toInstant(TimeZone.UTC)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
