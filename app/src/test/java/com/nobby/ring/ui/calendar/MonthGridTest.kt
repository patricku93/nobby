package com.nobby.ring.ui.calendar

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthGridTest {
    private fun day(d: Int) = DayInfo(LocalDate(2026, 11, d), if (d in 22..28) DayPhase.PAUSE else DayPhase.WEAR, null, false, false)

    // 1 Nov 2026 is a Sunday: six leading blanks, Nov d sits at index d + 5.
    private val cells: List<DayInfo?> = List(6) { null } + (1..30).map(::day)

    @Test
    fun `bands round off where the phase changes or the week row ends`() {
        assertEquals(BandEdges(start = true, end = true), bandEdges(cells, 6))     // Sun Nov 1, alone in its row
        assertEquals(BandEdges(start = true, end = false), bandEdges(cells, 7))    // Mon Nov 2 starts a row
        assertEquals(BandEdges(start = false, end = true), bandEdges(cells, 26))   // Sat Nov 21, last wear day
        assertEquals(BandEdges(start = true, end = true), bandEdges(cells, 27))    // Sun Nov 22, first pause day, row end
        assertEquals(BandEdges(start = true, end = false), bandEdges(cells, 28))   // Mon Nov 23
        assertEquals(BandEdges(start = false, end = false), bandEdges(cells, 0))   // blank cell
    }
}
