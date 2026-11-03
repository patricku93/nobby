package com.nobby.ring.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey val eventId: String,
    val kind: String,
    val plannedAt: String,
    val confirmedAt: String,
)

@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey val date: String,
    val text: String,
    val updatedAt: String,
)

@Entity(tableName = "reminder_rule")
data class ReminderRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val target: String,
    val offsetDays: Int,
    val timeHour: Int?,
    val timeMinute: Int?,
    val offsetHours: Int?,
    val importance: String,
    val enabled: Boolean,
)

@Entity(tableName = "calendar_link")
data class CalendarLinkEntity(
    @PrimaryKey val eventId: String,
    val providerEventId: Long,
    val startAt: String,
    val title: String,
)

@Dao
interface EventLogDao {
    @Query("SELECT * FROM event_log ORDER BY plannedAt") fun all(): Flow<List<EventLogEntity>>
    @Query("SELECT * FROM event_log ORDER BY plannedAt") suspend fun allOnce(): List<EventLogEntity>
    @Upsert suspend fun upsert(entities: List<EventLogEntity>)
    @Query("DELETE FROM event_log WHERE eventId = :eventId") suspend fun delete(eventId: String)
    @Query("DELETE FROM event_log") suspend fun clear()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM note WHERE date = :date") fun byDate(date: String): Flow<NoteEntity?>
    @Query("SELECT date FROM note WHERE text != ''") fun datesWithNotes(): Flow<List<String>>
    @Upsert suspend fun upsert(note: NoteEntity)
    @Query("DELETE FROM note WHERE date = :date") suspend fun delete(date: String)
}

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rule ORDER BY id") fun all(): Flow<List<ReminderRuleEntity>>
    @Query("SELECT * FROM reminder_rule ORDER BY id") suspend fun allOnce(): List<ReminderRuleEntity>
    @Upsert suspend fun upsert(rule: ReminderRuleEntity): Long
    @Query("DELETE FROM reminder_rule WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM reminder_rule") suspend fun clear()

    @Transaction
    suspend fun replaceAll(rules: List<ReminderRuleEntity>) {
        clear()
        rules.forEach { upsert(it) }
    }
}

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_link") suspend fun allOnce(): List<CalendarLinkEntity>
    @Upsert suspend fun upsert(links: List<CalendarLinkEntity>)
    @Query("DELETE FROM calendar_link WHERE eventId IN (:ids)") suspend fun delete(ids: List<String>)
    @Query("DELETE FROM calendar_link") suspend fun clear()
}

@Database(
    entities = [EventLogEntity::class, NoteEntity::class, ReminderRuleEntity::class, CalendarLinkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NobbyDatabase : RoomDatabase() {
    abstract fun eventLogDao(): EventLogDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderRuleDao(): ReminderRuleDao
    abstract fun calendarLinkDao(): CalendarLinkDao
}
