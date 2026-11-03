package com.nobby.ring.domain.engine

import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduledEvent
import com.nobby.ring.domain.model.eventId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atTime
import kotlinx.datetime.plus

object ScheduleEngine {

    fun generate(config: ScheduleConfig): List<ScheduledEvent> {
        val method = config.method
        val horizonEnd = config.anchor.date.plus(config.horizonMonths, DateTimeUnit.MONTH).atTime(config.anchor.time)
        val events = mutableListOf<ScheduledEvent>()
        if (config.firstInsertPending) {
            // Not started yet: the first ring goes in at the anchor. Cycle index -1 keeps every "cycleIndex == 0" lookup
            // (the current cycle's REMOVE / INSERT) pointing at the cycle this insertion starts.
            events += ScheduledEvent(firstInsertId(config), EventKind.INSERT, config.anchor, FIRST_INSERT_CYCLE, config.anchor)
        }
        var cycleStart = config.anchor
        var cycleIndex = 0
        var skipPause = config.skipPauseOnce

        while (true) {
            val removeAt = cycleStart.date.plus(method.wearDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
            check(removeAt > cycleStart) { "Schedule does not advance" }
            if (removeAt >= horizonEnd) break

            val swap = method.pauseDays == 0 || skipPause
            if (swap) {
                events += ScheduledEvent(eventId(EventKind.SWAP, removeAt), EventKind.SWAP, removeAt, cycleIndex, cycleStart)
                skipPause = false
                cycleStart = removeAt
            } else {
                events += ScheduledEvent(eventId(EventKind.REMOVE, removeAt), EventKind.REMOVE, removeAt, cycleIndex, cycleStart)
                val insertAt = removeAt.date.plus(method.pauseDays, DateTimeUnit.DAY).atTime(method.timeOfDay)
                if (insertAt >= horizonEnd) break
                events += ScheduledEvent(eventId(EventKind.INSERT, insertAt), EventKind.INSERT, insertAt, cycleIndex, cycleStart)
                cycleStart = insertAt
            }
            cycleIndex += 1
        }
        return events
    }

    /** Cycle index of the first insertion of a schedule that has not started yet. */
    const val FIRST_INSERT_CYCLE = -1

    /** Id of the first insertion while [ScheduleConfig.firstInsertPending] is set. */
    fun firstInsertId(config: ScheduleConfig): String = eventId(EventKind.INSERT, config.anchor)
}
