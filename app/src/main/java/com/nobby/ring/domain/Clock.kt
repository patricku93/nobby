package com.nobby.ring.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface NobbyClock {
    fun now(): LocalDateTime
    val zone: TimeZone
}

class SystemClock : NobbyClock {
    override val zone: TimeZone get() = TimeZone.currentSystemDefault()
    override fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(zone)
}
