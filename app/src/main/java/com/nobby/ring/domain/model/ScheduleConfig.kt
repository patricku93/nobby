package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

data class ScheduleConfig(
    val method: Method,
    /** Start of the current cycle (cycle index 0): the moment the current ring went in. */
    val anchor: LocalDateTime,
    val horizonMonths: Int = 12,
    /** One-off: the next REMOVE becomes a SWAP and the following cycle starts there. */
    val skipPauseOnce: Boolean = false,
    /**
     * "Not started yet": no ring is in. The schedule opens with a first INSERT at [anchor] (cycle index -1); the wear
     * phase begins only when that insertion is confirmed, which clears the flag.
     */
    val firstInsertPending: Boolean = false,
) {
    init {
        require(method.wearDays in MIN_WEAR..MAX_WEAR) {
            "wearDays must be in $MIN_WEAR..$MAX_WEAR but was ${method.wearDays}"
        }
        require(method.pauseDays in MIN_PAUSE..MAX_PAUSE) {
            "pauseDays must be in $MIN_PAUSE..$MAX_PAUSE but was ${method.pauseDays}"
        }
        require(horizonMonths in MIN_HORIZON..MAX_HORIZON) {
            "horizonMonths must be in $MIN_HORIZON..$MAX_HORIZON but was $horizonMonths"
        }
    }

    companion object {
        const val MIN_WEAR = 1
        const val MAX_WEAR = 35
        const val MIN_PAUSE = 0
        const val MAX_PAUSE = 14
        const val MIN_HORIZON = 1
        const val MAX_HORIZON = 24
        const val SAFE_MAX_WEAR = 28
        const val SAFE_MAX_PAUSE = 7
    }
}
