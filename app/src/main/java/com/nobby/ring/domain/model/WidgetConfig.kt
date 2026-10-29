package com.nobby.ring.domain.model

enum class WidgetMode { SINGLE, MULTI }
enum class WidgetRow { NEXT_ANY, NEXT_REMOVE, NEXT_INSERT, OVERDUE, CYCLE_DAY }
data class WidgetConfig(
    val mode: WidgetMode = WidgetMode.SINGLE,
    val rows: List<WidgetRow> = listOf(WidgetRow.NEXT_ANY, WidgetRow.CYCLE_DAY, WidgetRow.OVERDUE),
) {
    companion object { const val MAX_ROWS = 3 }
}
