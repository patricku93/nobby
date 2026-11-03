package com.nobby.ring.domain.model

enum class WidgetMode { SINGLE, MULTI }
enum class WidgetRow { NEXT_ANY, NEXT_REMOVE, NEXT_INSERT, OVERDUE, CYCLE_DAY }

/** Single-mode look: the Home bead bracelet as a picture ([RING]) or the v2.0 number layout ([CLASSIC]). */
enum class WidgetStyle { RING, CLASSIC }

data class WidgetConfig(
    val mode: WidgetMode = WidgetMode.SINGLE,
    val rows: List<WidgetRow> = listOf(WidgetRow.NEXT_ANY, WidgetRow.CYCLE_DAY, WidgetRow.OVERDUE),
    /** New installs get the ring; installs from v2.0 keep [WidgetStyle.CLASSIC] (see WidgetStyleMigration). */
    val style: WidgetStyle = WidgetStyle.RING,
    /** Background opacity in percent, 0..100 in [OPACITY_STEP] steps; text, ring, mascot and Done stay opaque. */
    val backgroundOpacity: Int = MAX_OPACITY,
) {
    companion object {
        const val MAX_ROWS = 3
        const val OPACITY_STEP = 10
        const val MAX_OPACITY = 100

        /** Clamps to 0..100 and rounds to the nearest 10 % step. */
        fun snapOpacity(value: Int): Int =
            ((value.coerceIn(0, MAX_OPACITY) + OPACITY_STEP / 2) / OPACITY_STEP * OPACITY_STEP).coerceAtMost(MAX_OPACITY)
    }
}
