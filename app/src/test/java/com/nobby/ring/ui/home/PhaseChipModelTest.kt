package com.nobby.ring.ui.home

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseChipModelTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0))
    private val removed = EventLog("REMOVE@2026-11-22T21:00", EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), LocalDateTime(2026, 11, 22, 21, 0))
    private fun at(now: LocalDateTime, logs: List<EventLog> = emptyList()) =
        PhaseChipModel.from(ScheduleRepository.buildState(config, logs, now))

    @Test
    fun `normal wear day`() = assertEquals(PhaseChipModel.InPhase(PhaseKind.WEAR, 12, 21), at(LocalDateTime(2026, 11, 12, 12, 0)))

    @Test
    fun `due day before the planned time is clamped to the phase length`() =
        assertEquals(PhaseChipModel.InPhase(PhaseKind.WEAR, 21, 21), at(LocalDateTime(2026, 11, 22, 10, 0)))

    @Test
    fun `removal two days overdue shows days over, not day 24 of 21`() =
        assertEquals(PhaseChipModel.OverDays(PhaseKind.WEAR, 2), at(LocalDateTime(2026, 11, 24, 22, 0)))

    @Test
    fun `overdue on the same day shows hours`() =
        assertEquals(PhaseChipModel.OverHours(PhaseKind.WEAR, 2), at(LocalDateTime(2026, 11, 22, 23, 30)))

    @Test
    fun `insertion two days overdue shows the pause over, not day 9 of 7 (v1_1 fix 1)`() =
        assertEquals(PhaseChipModel.OverDays(PhaseKind.PAUSE, 2), at(LocalDateTime(2026, 12, 1, 22, 0), listOf(removed)))
}
