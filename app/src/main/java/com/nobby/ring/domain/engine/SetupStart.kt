package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.minus

/** Onboarding's first answer: where the user stands with the ring right now. */
enum class SetupStatus { RING_IN, RING_OUT, NOT_STARTED }

/** The current cycle's removal to confirm through the normal confirmation path (same id, tolerance and pause bound). */
data class SetupRemoval(val eventId: String, val confirmedAt: LocalDateTime, val policy: ShiftPolicy)

sealed interface SetupPlan {
    /** Save [config]; then, when [removal] is set, confirm it like a normal confirmation. */
    data class Ready(val config: ScheduleConfig, val removal: SetupRemoval? = null) : SetupPlan

    /** An insertion or removal that has not happened yet. */
    data object InFuture : SetupPlan

    /** A first insertion planned before now. */
    data object InPast : SetupPlan

    /** The ring came out longer ago than the pause allows: ask for the planned insertion instead. */
    data object PauseOver : SetupPlan
}

/**
 * Turns the onboarding status answer into the schedule to save. Pure: the caller saves [SetupPlan.Ready.config] and
 * confirms [SetupPlan.Ready.removal] through ScheduleRepository, so a "ring is out" start is stored exactly like a
 * removal confirmed in the app.
 *
 * - Ring is in at [at]: the anchor is that insertion (an old one simply makes the removal overdue).
 * - Ring is out since [at]: the anchor is [at] minus the wear days at the routine time (the engine's rule), and that
 *   cycle's removal is confirmed at [at]. The removal is bounded like any confirmation
 *   ([ConfirmAction.removeShiftDays]); when the resulting insertion is already past, the pause is over.
 * - Not started, first insertion at [at] (now or later): the schedule waits for that insertion.
 *
 * Times compare against the real [now], seconds included, the same clock OverdueCalculator uses: an event planned
 * at or before [now] is already overdue, so a start or insertion has to lie after it (in practice: from the next
 * minute, as the pickers have no seconds). A past insertion or removal may be up to [now].
 */
object SetupStart {

    fun plan(status: SetupStatus, method: Method, at: LocalDateTime, now: LocalDateTime): SetupPlan = when (status) {
        SetupStatus.RING_IN -> if (at > now) SetupPlan.InFuture else SetupPlan.Ready(ScheduleConfig(method, at))
        SetupStatus.RING_OUT -> if (at > now) SetupPlan.InFuture else ringOut(method, at, now)
        SetupStatus.NOT_STARTED ->
            if (at <= now) SetupPlan.InPast else SetupPlan.Ready(ScheduleConfig(method, at, firstInsertPending = true))
    }

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
        return SetupPlan.Ready(config, SetupRemoval(remove.id, removedAt, policy))
    }
}
