package com.nobby.ring.data.calendar

import android.content.Context
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            val state = schedule.currentState() ?: return@withLock 0
            val desired = state.pendingEvents.map { e ->
                CalendarEntry(e.id, titleFor(e.kind), e.plannedAt, 30, CalendarEntry.marker(e.id))
            }
            val links = linkDao.allOnce().map { CalendarLink(it.eventId, it.providerEventId, LocalDateTime.parse(it.startAt), it.title) }
            val diff = CalendarDiff.compute(desired, links)

            // Persist each link right after its provider write so a mid-sync failure never orphans an event.
            diff.delete.forEach {
                source.delete(it.providerEventId)
                linkDao.delete(listOf(it.eventId))
            }
            diff.update.forEach { (link, entry) ->
                source.update(link.providerEventId, entry, clock.zone)
                linkDao.upsert(listOf(CalendarLinkEntity(entry.eventId, link.providerEventId, entry.startAt.toString(), entry.title)))
            }
            diff.create.forEach { entry ->
                val id = source.insert(calendarId, entry, clock.zone)
                linkDao.upsert(listOf(CalendarLinkEntity(entry.eventId, id, entry.startAt.toString(), entry.title)))
            }
            syncStore.markSynced(clock.now())
            diff.create.size + diff.update.size + diff.delete.size
        }
    }.onFailure { if (it is CancellationException) throw it }

    /** Deletes every linked provider event, clears the links and turns sync off. */
    suspend fun disconnect() = mutex.withLock {
        linkDao.allOnce().forEach { runCatching { source.delete(it.providerEventId) } }
        linkDao.clear()
        syncStore.save(enabled = false, calendarId = null)
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
