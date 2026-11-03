package com.nobby.ring.ui.calendar

import com.nobby.ring.data.repo.ScheduleRepository
import com.nobby.ring.domain.engine.ConfirmAction
import com.nobby.ring.domain.engine.ScheduleEngine
import com.nobby.ring.domain.engine.ShiftPolicy
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.EventLog
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.PhaseKind
import com.nobby.ring.domain.model.ScheduleConfig
import com.nobby.ring.domain.model.ScheduleState
import com.nobby.ring.ui.home.PhaseChipModel
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selected-day card must never contradict Home: today always shows Home's phase chip, days covered by an overdue
 * event show the overdue state, and "day X of N" never exceeds N.
 */
class DayInfoOverdueTest {
    private val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 1, 21, 0), 3)
    private val remove = ScheduleEngine.generate(config).first { it.kind == EventKind.REMOVE }
    private val removedOnTime = EventLog(remove.id, EventKind.REMOVE, remove.plannedAt, remove.plannedAt)

    private fun days(state: ScheduleState, year: Int, month: Int) = DayInfoBuilder.build(state, year, month, emptySet())
    private fun day(state: ScheduleState, date: LocalDate) = days(state, date.year, date.monthNumber).first { it.date == date }

    /** Today's card equals Home's chip; no in-phase card anywhere in Oct to Jan says more than "day N of N". */
    private fun assertConsistent(state: ScheduleState) {
        assertEquals(PhaseChipModel.from(state), day(state, state.now.date).card)
        val all = listOf(10 to 2026, 11 to 2026, 12 to 2026, 1 to 2027).flatMap { (m, y) -> days(state, y, m) }
        all.forEach { d ->
            assertTrue("${d.date}: day ${d.dayInPhase} of ${d.phaseLength}", d.dayInPhase <= d.phaseLength)
            val card = d.card
            if (card is PhaseChipModel.InPhase) assertTrue("${d.date}: $card", card.day <= card.length)
        }
    }

    @Test
    fun `overdue removal today reads like Home, not pause day 3 of 7`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 24, 22, 0))
        val today = day(state, LocalDate(2026, 11, 24))
        assertEquals(PhaseChipModel.OverDays(PhaseKind.WEAR, 2), today.card)
        assertConsistent(state)
    }

    @Test
    fun `past days covered by an overdue removal show the overdue amount at the end of that day`() {
        val state = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 24, 22, 0))
        // Planned 22 Nov 21:00: 3 h late at midnight, 27 h late at the end of 23 Nov.
        assertEquals(PhaseChipModel.OverHours(PhaseKind.WEAR, 3), day(state, LocalDate(2026, 11, 22)).card)
        assertEquals(PhaseChipModel.OverDays(PhaseKind.WEAR, 1), day(state, LocalDate(2026, 11, 23)).card)
        // Before the due day the plan is shown unchanged.
        assertEquals(PhaseChipModel.InPhase(PhaseKind.WEAR, 21, 21), day(state, LocalDate(2026, 11, 21)).card)
    }

    @Test
    fun `overdue insertion today reads like Home, not wear day 3 of 21`() {
        val state = ScheduleRepository.buildState(config, listOf(removedOnTime), LocalDateTime(2026, 12, 1, 22, 0))
        assertEquals(PhaseChipModel.OverDays(PhaseKind.PAUSE, 2), day(state, LocalDate(2026, 12, 1)).card)
        assertConsistent(state)
    }

    @Test
    fun `late removal confirmed with KEEP stays consistent`() {
        val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 25, 21, 0), ShiftPolicy.KEEP)
        listOf(26, 27, 28).forEach { d -> assertConsistent(ScheduleRepository.buildState(r.config, r.logs, LocalDateTime(2026, 11, d, 12, 0))) }
    }

    @Test
    fun `very late removal confirmed with KEEP or SHIFT stays consistent`() {
        ShiftPolicy.entries.forEach { policy ->
            val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 12, 2, 21, 0), policy)
            listOf(3, 6, 10).forEach { d -> assertConsistent(ScheduleRepository.buildState(r.config, r.logs, LocalDateTime(2026, 12, d, 12, 0))) }
        }
    }

    @Test
    fun `early removal stays consistent`() {
        ShiftPolicy.entries.forEach { policy ->
            val r = ConfirmAction.confirm(config, remove, emptyList(), LocalDateTime(2026, 11, 19, 21, 0), policy)
            listOf(20, 23, 26).forEach { d -> assertConsistent(ScheduleRepository.buildState(r.config, r.logs, LocalDateTime(2026, 11, d, 12, 0))) }
        }
    }

    @Test
    fun `early and late insertion stay consistent`() {
        val insert = ScheduleEngine.generate(config).first { it.kind == EventKind.INSERT }
        listOf(LocalDateTime(2026, 11, 26, 21, 0), LocalDateTime(2026, 12, 2, 21, 0)).forEach { at ->
            ShiftPolicy.entries.forEach { policy ->
                val r = ConfirmAction.confirm(config, insert, listOf(removedOnTime), at, policy)
                listOf(0, 5, 20, 24).forEach { offset ->
                    val now = at.date.plus(offset, DateTimeUnit.DAY).atTime(12, 0)
                    assertConsistent(ScheduleRepository.buildState(r.config, r.logs, now))
                }
            }
        }
    }

    @Test
    fun `a wear window longer than the method never reads day 24 of 21`() {
        // Pinned regression for the ledger's "Tag 24 von 21": events planned for 24 wear days under a 21-day method.
        val longer = config.copy(method = Method.ring(24, 7, LocalTime(21, 0)))
        val base = ScheduleRepository.buildState(config, emptyList(), LocalDateTime(2026, 11, 10, 12, 0))
        val state = base.copy(events = ScheduleEngine.generate(longer))
        val d = day(state, LocalDate(2026, 11, 24))
        assertEquals(DayPhase.WEAR, d.phase)
        assertEquals(21, d.dayInPhase)
        assertEquals(PhaseChipModel.InPhase(PhaseKind.WEAR, 21, 21), d.card)
    }
}
