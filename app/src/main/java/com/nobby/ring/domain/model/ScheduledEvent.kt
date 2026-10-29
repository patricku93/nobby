package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

enum class EventKind { REMOVE, INSERT, SWAP }

data class ScheduledEvent(
    val id: String,
    val kind: EventKind,
    val plannedAt: LocalDateTime,
    val cycleIndex: Int,
    val cycleStart: LocalDateTime,
) {
    /** True when confirming this event starts a new cycle (ring goes in). */
    val startsCycle: Boolean get() = kind != EventKind.REMOVE
}

fun eventId(kind: EventKind, plannedAt: LocalDateTime): String = "${kind.name}@$plannedAt"
