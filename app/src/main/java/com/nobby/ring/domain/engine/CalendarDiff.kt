package com.nobby.ring.domain.engine

import kotlinx.datetime.LocalDateTime

data class CalendarEntry(
    val eventId: String,
    val title: String,
    val startAt: LocalDateTime,
    val durationMinutes: Int = 30,
    val description: String,
) {
    companion object {
        const val MARKER_PREFIX = "nobby:"
        fun marker(eventId: String): String = MARKER_PREFIX + eventId
    }
}

data class CalendarLink(
    val eventId: String,
    val providerEventId: Long,
    val startAt: LocalDateTime,
    val title: String,
)

data class CalendarDiffResult(
    val create: List<CalendarEntry>,
    val update: List<Pair<CalendarLink, CalendarEntry>>,
    val delete: List<CalendarLink>,
)

object CalendarDiff {
    fun compute(desired: List<CalendarEntry>, links: List<CalendarLink>): CalendarDiffResult {
        val byId = links.associateBy { it.eventId }
        val desiredIds = desired.map { it.eventId }.toSet()
        val create = desired.filter { it.eventId !in byId }
        val update = desired.mapNotNull { entry ->
            val link = byId[entry.eventId] ?: return@mapNotNull null
            if (link.startAt != entry.startAt || link.title != entry.title) link to entry else null
        }
        val delete = links.filter { it.eventId !in desiredIds }
        return CalendarDiffResult(create, update, delete)
    }
}
