package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus

/** Onboarding's first answer: where the user stands with the ring right now. */
enum class SetupStatus { RING_IN, RING_OUT, NOT_STARTED }

sealed interface SetupPlan {
    /** Save [logs] and then [config] together (ScheduleRepository.startWith): the log rows come first, as in a confirmation. */
    data class Ready(val config: ScheduleConfig, val logs: List<EventLog> = emptyList()) : SetupPlan

    /** An insertion or removal that has not happened yet. */
    data object InFuture : SetupPlan

    /** A first insertion planned at or before now. */
    data object InPast : SetupPlan

    /** The ring came out longer ago than the pause allows: ask for the planned insertion instead. */
    data object PauseOver : SetupPlan
}

/**
 * Turns the onboarding status answer into the schedule and log rows to save. Pure: the caller writes both through
 * ScheduleRepository.startWith, so a "ring is out" start is stored exactly like a removal confirmed in the app.
 *
 * - Ring is in at [at]: the anchor is that insertion (an old one simply makes the removal overdue).
 * - Ring is out since [at]: the anchor is [at] minus the wear days at the routine time (the engine's rule), and that
 *   cycle's removal is confirmed at [at] through [ConfirmAction.confirm] with the one-tap policy, so the id, the
 *   tolerance and the pause bound ([ConfirmAction.removeShiftDays]) are those of any confirmation. When the bounded
 *   insertion is not ahead any more, the pause is over.
 * - Not started, first insertion at [at] (after now): the schedule waits for that insertion. With [removedAt] (the
 *   pause is over) that removal is logged at its real time, so the break since then counts for the pause safety
 *   check of the new ring exactly like a logged removal before any insertion ([OverdueCalculator.removalBeforeStart]).
 *
 * Times compare against the real [now], seconds included, the same clock OverdueCalculator uses: an event planned
 * at or before [now] is already overdue, so a start or insertion has to lie after it (in practice: from the next
 * minute, as the pickers have no seconds). A past insertion or removal may be up to [now].
 */
object SetupStart {

    fun plan(status: SetupStatus, method: Method, at: LocalDateTime, now: LocalDateTime, removedAt: LocalDateTime? = null): SetupPlan =
        when (status) {
            SetupStatus.RING_IN -> if (at > now) SetupPlan.InFuture else SetupPlan.Ready(ScheduleConfig(method, at))
            SetupStatus.RING_OUT -> if (at > now) SetupPlan.InFuture else ringOut(method, at, now)
            SetupStatus.NOT_STARTED -> if (at <= now) SetupPlan.InPast else notStarted(method, at, removedAt)
        }

    private fun notStarted(method: Method, at: LocalDateTime, removedAt: LocalDateTime?): SetupPlan.Ready = SetupPlan.Ready(
        ScheduleConfig(method, at, firstInsertPending = true),
        listOfNotNull(removedAt?.let { EventLog(eventId(EventKind.REMOVE, it), EventKind.REMOVE, it, it) }),
    )

    private fun ringOut(method: Method, removedAt: LocalDateTime, now: LocalDateTime): SetupPlan {
        val anchor = removedAt.date.minus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
        val config = ScheduleConfig(method, anchor)
        // No pause in the rhythm (continuous use): there is no REMOVE, so a ring that is out is due back in now.
        val remove = ScheduleEngine.generate(config).firstOrNull { it.kind == EventKind.REMOVE && it.cycleIndex == 0 }
            ?: return SetupPlan.PauseOver
        val policy = ConfirmAction.quickConfirmPolicy(remove, removedAt)
        val confirmed = ConfirmAction.confirm(config, remove, emptyList(), removedAt, policy)
        val insert = OverdueCalculator.nextPending(ScheduleEngine.generate(confirmed.config), confirmed.logs)
        if (insert == null || insert.kind != EventKind.INSERT || insert.plannedAt <= now) return SetupPlan.PauseOver
        return SetupPlan.Ready(confirmed.config, confirmed.logs)
    }

    /**
     * A waiting schedule whose start is not ahead any more is a started one: the ring is in since that time, the same
     * result as "ring is in" (Settings > Apply, I3). So no "not started" schedule with an overdue first insertion and
     * no safety check can be saved. At or before [now] counts as past, as in [plan].
     */
    fun startedBy(config: ScheduleConfig, now: LocalDateTime): ScheduleConfig =
        if (config.firstInsertPending && config.anchor <= now) config.copy(firstInsertPending = false) else config
}
