package com.nobby.ring.data.repo

import com.nobby.ring.data.db.EventLogDao
import com.nobby.ring.data.db.EventLogEntity
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.engine.ConfigChange
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.CycleCalculator
import com.nobby.ring.domain.engine.OverdueCalculator
import com.nobby.ring.domain.engine.ScheduleEngine
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val configStore: MethodConfigStore,
    private val eventLogDao: EventLogDao,
    private val clock: NobbyClock,
) {
    /**
     * Current schedule state. `clock.now()` is sampled only when the config or the event log emits; this flow is
     * not a ticker. UI should re-collect on resume, and the widget/midnight worker calls [currentState].
     */
    val state: Flow<ScheduleState?> = combine(configStore.config, eventLogDao.all()) { config, logEntities ->
        config?.let { build(it, logEntities.map(::toDomain), clock.now()) }
    }

    suspend fun currentState(): ScheduleState? = state.first()

    /** Serializes the read-modify-write paths: a notification Done can run while Settings applies (M-A). */
    private val writeLock = Mutex()

    /**
     * Saves an edited schedule. A removal already confirmed for the current cycle is re-keyed to the edited schedule's
     * REMOVE id and its INSERT bounded like a KEEP confirmation ([ConfigChange.carriedRemoval]), so the pause stays a
     * pause after Apply and is never planned longer than the configured one. The new key is written BEFORE the config
     * and the old one dropped AFTER it: every intermediate state has the removal logged under the id the stored config
     * expects (an extra unknown id is ignored, as in [confirm]); a stale duplicate left by a crash in between is
     * removed by the next Apply.
     */
    suspend fun updateConfig(config: ScheduleConfig) = writeLock.withLock {
        val logs = eventLogDao.allOnce().map(::toDomain)
        val carried = configStore.config.first()?.let { old -> ConfigChange.carriedRemoval(old, config, logs) }
        val saved = carried?.config ?: config
        carried?.let { eventLogDao.upsert(listOf(toEntity(it.to))) }
        configStore.save(saved)
        carried?.takeIf { it.from.eventId != it.to.eventId }?.let { eventLogDao.delete(it.from.eventId) }
        dropStaleRemovalCopies(saved)
    }

    /** Deletes REMOVE rows that duplicate the current cycle's removal (same planned and confirmed time, other id). */
    private suspend fun dropStaleRemovalCopies(config: ScheduleConfig) {
        val rows = eventLogDao.allOnce()
        val currentId = CycleCalculator.currentRemoveId(config)
        val current = rows.firstOrNull { it.eventId == currentId } ?: return
        rows.filter {
            it.kind == EventKind.REMOVE.name && it.eventId != currentId &&
                it.plannedAt == current.plannedAt && it.confirmedAt == current.confirmedAt
        }.forEach { eventLogDao.delete(it.eventId) }
    }

    suspend fun skipPause() = writeLock.withLock {
        val config = configStore.config.first() ?: return@withLock
        configStore.save(config.copy(skipPauseOnce = true))
    }

    suspend fun cancelSkipPause() = writeLock.withLock {
        val config = configStore.config.first() ?: return@withLock
        configStore.save(config.copy(skipPauseOnce = false))
    }

    suspend fun confirm(eventId: String, confirmedAt: LocalDateTime, policy: ShiftPolicy) = writeLock.withLock {
        val config = configStore.config.first() ?: return@withLock
        val logs = eventLogDao.allOnce().map(::toDomain)
        val event = ScheduleEngine.generate(config).firstOrNull { it.id == eventId } ?: return@withLock
        val result = ConfirmAction.confirm(config, event, logs, confirmedAt, policy)
        // Write logs BEFORE the config. The intermediate state "new log + old config" is harmless (a log keyed by an
        // unknown id is ignored by nextPending), whereas "new config + no log" would briefly show the event as pending again.
        eventLogDao.upsert(result.logs.map(::toEntity))
        configStore.save(result.config)
    }

    fun build(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): ScheduleState = buildState(config, logs, now)

    private fun toDomain(e: EventLogEntity) = EventLog(
        e.eventId, EventKind.valueOf(e.kind), LocalDateTime.parse(e.plannedAt), LocalDateTime.parse(e.confirmedAt),
    )

    private fun toEntity(l: EventLog) = EventLogEntity(l.eventId, l.kind.name, l.plannedAt.toString(), l.confirmedAt.toString())

    companion object {
        /** Pure state builder, usable without Hilt (tests, widget). */
        fun buildState(config: ScheduleConfig, logs: List<EventLog>, now: LocalDateTime): ScheduleState {
            val events = ScheduleEngine.generate(config)
            return ScheduleState(
                config = config,
                events = events,
                logs = logs,
                now = now,
                position = CycleCalculator.positionAt(config, logs, now),
                nextEvent = OverdueCalculator.nextPending(events, logs),
                overdue = OverdueCalculator.overdue(config, events, logs, now),
            )
        }
    }
}
