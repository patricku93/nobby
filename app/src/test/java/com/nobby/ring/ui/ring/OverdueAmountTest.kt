package com.nobby.ring.ui.ring

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** One overdue-amount rule for every surface: under 24 h whole hours (at least 1), from 24 h whole elapsed days. */
class OverdueAmountTest {
    @Test fun `half an hour is one hour`() = assertEquals(OverdueAmount.Hours(1), OverdueAmount.of(30.minutes))

    @Test fun `23 h 59 min is still hours`() = assertEquals(OverdueAmount.Hours(23), OverdueAmount.of(23.hours + 59.minutes))

    @Test fun `exactly 24 h is one day`() = assertEquals(OverdueAmount.Days(1), OverdueAmount.of(24.hours))

    @Test fun `49 h is two days`() = assertEquals(OverdueAmount.Days(2), OverdueAmount.of(49.hours))

    @Test
    fun `3 h across midnight counts hours, not a calendar day`() {
        // Removal planned 22:00, now 01:00 the next day.
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(22, 0)), LocalDateTime(2026, 11, 1, 22, 0))
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 23, 1, 0))
        assertEquals(OverdueAmount.Hours(3), OverdueAmount.of(state))
    }
}
