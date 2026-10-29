package com.nobby.ring.domain.model

import kotlinx.datetime.LocalDateTime

data class ScheduleConfig(
    val method: Method,
    /** Start of the current cycle (cycle index 0): the moment the current ring went in. */
    val anchor: LocalDateTime,
    val horizonMonths: Int = 12,
    /** One-off: the next REMOVE becomes a SWAP and the following cycle starts there. */
    val skipPauseOnce: Boolean = false,
) {
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
