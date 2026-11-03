package com.nobby.ring.data.calendar

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nobby.ring.R
import com.nobby.ring.data.db.CalendarLinkDao
import com.nobby.ring.data.db.CalendarLinkEntity
import com.nobby.ring.data.prefs.CalendarSyncStore
import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.CalendarDiff
import com.nobby.ring.domain.engine.CalendarEntry
import com.nobby.ring.domain.engine.CalendarLink
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarSyncer @Inject constructor(
    private val source: CalendarSource,
    private val linkDao: CalendarLinkDao,
    private val syncStore: CalendarSyncStore,
    private val schedule: ScheduleRepository,
    private val clock: NobbyClock,
    @ApplicationContext private val context: Context,
) : ScheduleChangeListener {
    private val mutex = Mutex()

    override suspend fun onScheduleChanged() {
        if (!syncStore.enabled.first()) return
        // WorkManager may be uninitialised (for example under Robolectric); a failed enqueue must not break callers.
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<CalendarSyncWorker>().build(),
            )
        }
    }

    /** Returns the number of provider writes performed (create + update + delete). Safe to call concurrently. */
    suspend fun syncNow(): Result<Int> = runCatching {
        mutex.withLock {
            if (!syncStore.enabled.first()) return@withLock 0
            val calendarId = syncStore.calendarId.first() ?: return@withLock 0
            // The calendar may have been deleted or made read-only outside the app: write nothing, keep lastSync,
            // and drop the links (their provider events went with the calendar) so a later pick starts clean.
            if (source.listWritable().none { it.id == calendarId }) {
                linkDao.clear()
                throw CalendarUnavailableException(calendarId)
            }
            val state = schedule.currentState() ?: return@withLock 0
            val desired = state.pendingEvents.map { e ->
                CalendarEntry(e.id, titleFor(e.kind), e.plannedAt, 30, CalendarEntry.marker(e.id))
            }
            val links = linkDao.allOnce().map { CalendarLink(it.eventId, it.providerEventId, LocalDateTime.parse(it.startAt), it.title) }
            val diff = CalendarDiff.compute(desired, links)

            // Persist each link right after its provider write so a mid-sync failure never orphans an event.
            // A linked provider id is only written to while it is still NoBby's own event in the chosen calendar:
            // provider ids can be reused or the event edited elsewhere, and we must never touch a user's event.
            diff.delete.forEach {
                if (source.isOwnEvent(it.providerEventId, calendarId)) source.delete(it.providerEventId)
                linkDao.delete(listOf(it.eventId))
            }
            // NonCancellable: a REPLACE-cancelled worker must not leave a provider write without its link.
            diff.update.forEach { (link, entry) ->
                withContext(NonCancellable) {
                    val updated = source.isOwnEvent(link.providerEventId, calendarId) &&
                        source.update(link.providerEventId, entry, clock.zone) > 0
                    // Not ours any more, or gone (0 rows): drop the stale link by re-creating the event and relinking.
                    val providerId = if (updated) link.providerEventId else source.insert(calendarId, entry, clock.zone)
                    linkDao.upsert(listOf(CalendarLinkEntity(entry.eventId, providerId, entry.startAt.toString(), entry.title)))
                }
            }
            diff.create.forEach { entry ->
                withContext(NonCancellable) {
                    val id = source.insert(calendarId, entry, clock.zone)
                    linkDao.upsert(listOf(CalendarLinkEntity(entry.eventId, id, entry.startAt.toString(), entry.title)))
                }
            }
            syncStore.markSynced(clock.now())
            diff.create.size + diff.update.size + diff.delete.size
        }
    }.onFailure {
        if (it is CancellationException) throw it
        if (it is CalendarUnavailableException) { Log.w("NoBby", "Selected calendar is gone"); return@onFailure }
        Log.e("NoBby", "Calendar sync failed", it)
    }

    /** Deletes every linked provider event, clears the links and turns sync off. */
    suspend fun disconnect() = mutex.withLock {
        deleteLinkedEventsLocked()
        syncStore.save(enabled = false, calendarId = null)
    }

    /**
     * Deletes every linked provider event and clears the links, leaving [CalendarSyncStore] untouched
     * (used when switching calendars so the enabled switch does not flicker).
     */
    suspend fun clearLinks() = mutex.withLock { deleteLinkedEventsLocked() }

    /**
     * Drops the links whose provider event id is in [providerIds], for events removed outside the sync
     * (for example by the clean-up tool). Does NOT trigger a sync: NoBby's own events that are still
     * pending are recreated by the next schedule change or "Sync now".
     */
    suspend fun forget(providerIds: Collection<Long>) = mutex.withLock {
        if (providerIds.isEmpty()) return@withLock
        val ids = providerIds.toSet()
        val eventIds = linkDao.allOnce().filter { it.providerEventId in ids }.map { it.eventId }
        if (eventIds.isNotEmpty()) linkDao.delete(eventIds)
    }

    private suspend fun deleteLinkedEventsLocked() {
        // Links are always into the stored calendar; without one we cannot verify ownership, so nothing is deleted.
        val calendarId = syncStore.calendarId.first()
        linkDao.allOnce().forEach {
            try {
                if (calendarId != null && source.isOwnEvent(it.providerEventId, calendarId)) source.delete(it.providerEventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NoBby", "Failed to delete calendar event ${it.providerEventId}", e)
            }
        }
        linkDao.clear()
    }

    private fun titleFor(kind: EventKind): String = context.getString(
        when (kind) {
            EventKind.REMOVE -> R.string.event_remove
            EventKind.INSERT -> R.string.event_insert
            EventKind.SWAP -> R.string.event_swap
        },
    )

    companion object { const val WORK_NAME = "calendar_sync" }
}
