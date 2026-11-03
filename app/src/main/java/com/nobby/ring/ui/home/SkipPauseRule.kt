package com.nobby.ring.ui.home

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleState

/**
 * When Home offers "Skip break" (or "Keep the break" while a skip is set). Skip-pause turns the current cycle's REMOVE
 * into a SWAP, so it only makes sense while that REMOVE (or the SWAP it became) is the next or overdue event. While an
 * INSERT is next the REMOVE is already confirmed, and toggling would turn it into a phantom overdue SWAP, so it is hidden.
 */
object SkipPauseRule {
    fun isOffered(state: ScheduleState): Boolean {
        val event = state.overdue?.event ?: state.nextEvent ?: return false
        return event.kind != EventKind.INSERT
    }
}
