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

    /**
     * Saves an edited schedule. A removal already confirmed for the current cycle is re-keyed to the edited schedule's
     * REMOVE id ([ConfigChange.carriedRemoval]), so the pause stays a pause after Apply. The new key is written BEFORE
     * the config and the old one dropped AFTER it: every intermediate state has the removal logged under the id the
     * stored config expects (an extra unknown id is ignored, as in [confirm]); a crash in between leaves at most a
     * stale duplicate row, never an orphaned removal.
     */
    suspend fun updateConfig(config: ScheduleConfig) {
        val carried = configStore.config.first()?.let { old ->
            ConfigChange.carriedRemoval(old, config, eventLogDao.allOnce().map(::toDomain))
        }
        carried?.let { eventLogDao.upsert(listOf(toEntity(it.to))) }
        configStore.save(config)
        carried?.let { eventLogDao.delete(it.from.eventId) }
    }

    suspend fun skipPause() {
        val config = configStore.config.first() ?: return
        configStore.save(config.copy(skipPauseOnce = true))
    }

    suspend fun cancelSkipPause() {
        val config = configStore.config.first() ?: return
        configStore.save(config.copy(skipPauseOnce = false))
    }

    suspend fun confirm(eventId: String, confirmedAt: LocalDateTime, policy: ShiftPolicy) {
        val config = configStore.config.first() ?: return
        val logs = eventLogDao.allOnce().map(::toDomain)
        val event = ScheduleEngine.generate(config).firstOrNull { it.id == eventId } ?: return
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
