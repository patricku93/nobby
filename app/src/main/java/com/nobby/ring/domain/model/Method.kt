package com.nobby.ring.domain.model

import kotlinx.datetime.LocalTime

enum class PhaseKind { WEAR, PAUSE }

data class Phase(val kind: PhaseKind, val days: Int)

data class Method(
    val name: String,
    val phases: List<Phase>,
    val timeOfDay: LocalTime,
) {
    val cycleLengthDays: Int get() = phases.sumOf { it.days }
    val wearDays: Int get() = phases.filter { it.kind == PhaseKind.WEAR }.sumOf { it.days }
    val pauseDays: Int get() = phases.filter { it.kind == PhaseKind.PAUSE }.sumOf { it.days }

    companion object {
        const val RING = "ring"
        fun ring(wearDays: Int, pauseDays: Int, timeOfDay: LocalTime): Method = Method(
            name = RING,
            phases = listOf(Phase(PhaseKind.WEAR, wearDays), Phase(PhaseKind.PAUSE, pauseDays)),
            timeOfDay = timeOfDay,
        )
    }
}
