package com.nobby.ring.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.nobby.ring.domain.engine.CalendarEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int)
data class FoundEvent(val id: Long, val title: String, val startMillis: Long)

interface CalendarSource {
    suspend fun listWritable(): List<DeviceCalendar>
    suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long
    suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone)
    suspend fun delete(providerId: Long)
    suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long): List<FoundEvent>
}

class DeviceCalendarSource @Inject constructor(@ApplicationContext private val context: Context) : CalendarSource {
    private val resolver get() = context.contentResolver

    override suspend fun listWritable(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(DeviceCalendar(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3))) }
        } ?: emptyList()
    }

    override suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long = withContext(Dispatchers.IO) {
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values(entry, zone).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) })
            ?: throw IllegalStateException("Calendar insert failed")
        ContentUris.parseId(uri)
    }

    override suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone) = withContext(Dispatchers.IO) {
        resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), values(entry, zone), null, null)
        Unit
    }

    override suspend fun delete(providerId: Long) = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), null, null)
        Unit
    }

    override suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long): List<FoundEvent> = withContext(Dispatchers.IO) {
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} BETWEEN ? AND ? AND ${CalendarContract.Events.DELETED} = 0"
        resolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, arrayOf("$calendarId", "$fromMillis", "$toMillis"), CalendarContract.Events.DTSTART)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(1) ?: ""
                    if (pattern.containsMatchIn(title)) add(FoundEvent(c.getLong(0), title, c.getLong(2)))
                }
            }
        } ?: emptyList()
    }

    private fun values(entry: CalendarEntry, zone: TimeZone): ContentValues {
        val start = entry.startAt.toInstant(zone)
        return ContentValues().apply {
            put(CalendarContract.Events.TITLE, entry.title)
            put(CalendarContract.Events.DESCRIPTION, entry.description)
            put(CalendarContract.Events.DTSTART, start.toEpochMilliseconds())
            put(CalendarContract.Events.DTEND, (start + entry.durationMinutes.minutes).toEpochMilliseconds())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
    }
}
