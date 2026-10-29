package com.nobby.ring.data.repo

import com.nobby.ring.data.db.NoteDao
import com.nobby.ring.data.db.NoteEntity
import com.nobby.ring.domain.NobbyClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import javax.inject.Inject

class NotesRepository @Inject constructor(private val dao: NoteDao, private val clock: NobbyClock) {
    fun note(date: LocalDate): Flow<String?> = dao.byDate(date.toString()).map { it?.text }
    fun datesWithNotes(): Flow<Set<LocalDate>> = dao.datesWithNotes().map { list -> list.map(LocalDate::parse).toSet() }
    suspend fun save(date: LocalDate, text: String) {
        if (text.isBlank()) dao.delete(date.toString())
        else dao.upsert(NoteEntity(date.toString(), text.trim(), clock.now().toString()))
    }
}
