package com.nobby.ring.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.repo.NotesRepository
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import javax.inject.Inject

data class CalendarUiState(
    val year: Int,
    val month: Int,
    val days: List<DayInfo> = emptyList(),
    val selected: LocalDate,
    val noteText: String = "",
)

/** An unsaved edit, bound to the date it was typed for so switching days never moves text to another day. */
private data class NoteDraft(val date: LocalDate, val text: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val schedule: ScheduleRepository,
    private val notes: NotesRepository,
    clock: NobbyClock,
) : ViewModel() {
    private val today = clock.now().date
    private val month = MutableStateFlow(LocalDate(today.year, today.monthNumber, 1))
    private val selected = MutableStateFlow(today)
    private val draft = MutableStateFlow<NoteDraft?>(null)

    private val storedNote = selected.flatMapLatest { notes.note(it) }

    private val noteText = combine(storedNote, draft, selected) { stored, d, sel ->
        d?.takeIf { it.date == sel }?.text ?: stored ?: ""
    }

    val uiState: StateFlow<CalendarUiState> = combine(schedule.state, notes.datesWithNotes(), month, selected, noteText) { state, noteDates, m, sel, text ->
        CalendarUiState(
            year = m.year,
            month = m.monthNumber,
            days = state?.let { DayInfoBuilder.build(it, m.year, m.monthNumber, noteDates) } ?: emptyList(),
            selected = sel,
            noteText = text,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(today.year, today.monthNumber, selected = today))

    init {
        viewModelScope.launch {
            draft.debounce(NOTE_DEBOUNCE_MS).collect { d -> if (d != null) notes.save(d.date, d.text) }
        }
    }

    fun prevMonth() = month.update { it.minus(1, DateTimeUnit.MONTH) }
    fun nextMonth() = month.update { it.plus(1, DateTimeUnit.MONTH) }

    fun select(date: LocalDate) {
        // Persist an in-flight edit for the previous day right away; the debounce would otherwise be superseded.
        draft.value?.let { pending -> viewModelScope.launch { notes.save(pending.date, pending.text) } }
        draft.value = null
        selected.value = date
    }

    fun setNote(text: String) {
        draft.value = NoteDraft(selected.value, text)
    }

    private companion object {
        const val NOTE_DEBOUNCE_MS = 500L
    }
}
