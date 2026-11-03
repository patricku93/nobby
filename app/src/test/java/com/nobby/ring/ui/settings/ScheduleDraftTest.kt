package com.nobby.ring.ui.settings

import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDraftTest {
    @Test
    fun `Apply keeps a schedule that has not started yet waiting for its first insertion`() {
        val config = ScheduleConfig(Method.ring(21, 7, LocalTime(21, 0)), LocalDateTime(2026, 11, 6, 21, 0), firstInsertPending = true)
        val edited = ScheduleDraft.from(config).copy(wearDays = 22).toConfig()
        assertTrue(edited.firstInsertPending)
    }
}
