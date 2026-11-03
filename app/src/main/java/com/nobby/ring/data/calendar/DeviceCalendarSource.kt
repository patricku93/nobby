package com.nobby.ring.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars as C
import android.util.Log
import com.nobby.ring.domain.engine.CalendarEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * [identity] tells apart two calendars that got the same provider id (ids are reused after a delete): account name,
 * account type, internal calendar name and the provider's _SYNC_ID when there is one. [writable] is false for read-only ones.
 */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val identity: String = "$account|$name",
    val writable: Boolean = true,
)
/**
 * True when a stored identity still describes [current]. An identity stored with an empty trailing _SYNC_ID
 * ("account|type|name|") means "sync id unknown": a sync id the provider assigns later does not make it another calendar.
 */
fun identityMatches(stored: String?, current: String): Boolean =
    stored == null || stored == current || (stored.endsWith("|") && current.startsWith(stored))

data class FoundEvent(val id: Long, val title: String, val startMillis: Long)

interface CalendarSource {
    suspend fun listWritable(): List<DeviceCalendar>
    /** Every calendar on the device incl. read-only ones, or null when the provider query itself failed (nothing is known). */
    suspend fun listAll(): List<DeviceCalendar>?
    suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long
    /** Returns the number of rows updated (0 when the provider event no longer exists). */
    suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone): Int
    suspend fun delete(providerId: Long): Boolean
    /** True when [providerId] is a live event in [calendarId] whose description carries NoBby's `nobby:` marker. */
    suspend fun isOwnEvent(providerId: Long, calendarId: Long): Boolean
    suspend fun search(calendarId: Long, pattern: Regex, fromMillis: Long, toMillis: Long): List<FoundEvent>
}

class DeviceCalendarSource @Inject constructor(@ApplicationContext private val context: Context) : CalendarSource {
    private val resolver get() = context.contentResolver

    override suspend fun listWritable(): List<DeviceCalendar> = listAll()?.filter { it.writable } ?: emptyList()

    override suspend fun listAll(): List<DeviceCalendar>? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            C._ID, C.CALENDAR_DISPLAY_NAME, C.ACCOUNT_NAME, C.CALENDAR_COLOR,
            C.ACCOUNT_TYPE, C.NAME, C._SYNC_ID, C.CALENDAR_ACCESS_LEVEL,
        )
        resolver.query(C.CONTENT_URI, projection, null, null, null)?.use { cur ->
            buildList {
                while (cur.moveToNext()) {
                    val account = cur.getString(2) ?: ""
                    val identity = listOf(account, cur.getString(4) ?: "", cur.getString(5) ?: "", cur.getString(6) ?: "").joinToString("|")
                    add(DeviceCalendar(cur.getLong(0), cur.getString(1) ?: "", account, cur.getInt(3), identity, cur.getInt(7) >= C.CAL_ACCESS_CONTRIBUTOR))
                }
            }
        }
    }

    override suspend fun insert(calendarId: Long, entry: CalendarEntry, zone: TimeZone): Long = withContext(Dispatchers.IO) {
        try {
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values(entry, zone).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) })
                ?: throw IllegalStateException("Calendar insert failed")
            ContentUris.parseId(uri)
        } catch (e: Exception) {
            Log.e("NoBby", "Calendar insert failed for calendar $calendarId", e)
            throw e
        }
    }

    override suspend fun update(providerId: Long, entry: CalendarEntry, zone: TimeZone): Int = withContext(Dispatchers.IO) {
        resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), values(entry, zone), null, null)
    }

    override suspend fun isOwnEvent(providerId: Long, calendarId: Long): Boolean = withContext(Dispatchers.IO) {
        val projection = arrayOf(CalendarContract.Events.DESCRIPTION, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DELETED)
        resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), projection, null, null, null)?.use { c ->
            c.moveToFirst() &&
                c.getString(0).orEmpty().startsWith(CalendarEntry.MARKER_PREFIX) &&
                c.getLong(1) == calendarId &&
                c.getInt(2) == 0
        } ?: false
    }

    override suspend fun delete(providerId: Long): Boolean = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId), null, null) >= 1
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
