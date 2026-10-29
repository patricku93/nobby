package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

data class EventLog(
    val eventId: String,
    val kind: EventKind,
    val plannedAt: LocalDateTime,
    val confirmedAt: LocalDateTime,
)
