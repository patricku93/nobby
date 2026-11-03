package com.nobby.ring.ui.ring

import com.nobby.ring.domain.model.ScheduleState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * How late an overdue event is, the one rule for every surface (Home ring, phase chip, overdue card, calendar card,
 * widget): under 24 h of elapsed delay whole hours (at least 1), from 24 h on whole elapsed days. A 3-hour delay that
 * crosses midnight is "3 h", never "1 day".
 */
sealed interface OverdueAmount {
    data class Hours(val hours: Int) : OverdueAmount
    data class Days(val days: Int) : OverdueAmount

    companion object {
        fun of(delay: Duration): OverdueAmount =
            if (delay < 24.hours) Hours(delay.inWholeHours.toInt().coerceAtLeast(1)) else Days(delay.inWholeDays.toInt())

        /** Null when nothing is overdue. */
        fun of(state: ScheduleState): OverdueAmount? = state.overdue?.let { of(it.delay) }
    }
}
