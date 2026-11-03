package com.nobby.ring.i18n

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PluralsTest {
    private fun q(id: Int, n: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().resources.getQuantityString(id, n, *args)

    @Test
    @Config(qualifiers = "en")
    fun `english plurals for 0, 1 and n`() {
        assertEquals("days until removal", q(R.plurals.ring_until_remove, 0))
        assertEquals("day until removal", q(R.plurals.ring_until_remove, 1))
        assertEquals("days until removal", q(R.plurals.ring_until_remove, 10))
        assertEquals("0 days overdue", q(R.plurals.card_overdue_days, 0, 0))
        assertEquals("1 day overdue", q(R.plurals.card_overdue_days, 1, 1))
        assertEquals("2 days overdue", q(R.plurals.card_overdue_days, 2, 2))
        assertEquals("in 1 day", q(R.plurals.widget_in_days, 1, 1))
        assertEquals("1 day before at 20:00", q(R.plurals.reminder_day_before, 1, 1, "20:00"))
    }

    @Test
    @Config(qualifiers = "de")
    fun `german plurals for 0, 1 and n`() {
        assertEquals("Tage bis Ring raus", q(R.plurals.ring_until_remove, 0))
        assertEquals("Tag bis Ring raus", q(R.plurals.ring_until_remove, 1))
        assertEquals("Tage bis Ring raus", q(R.plurals.ring_until_remove, 10))
        assertEquals("0 Tage überfällig", q(R.plurals.card_overdue_days, 0, 0))
        assertEquals("1 Tag überfällig", q(R.plurals.card_overdue_days, 1, 1))
        assertEquals("2 Tage überfällig", q(R.plurals.card_overdue_days, 2, 2))
        assertEquals("in 1 Tag", q(R.plurals.widget_in_days, 1, 1))
        assertEquals("in 3 Tagen", q(R.plurals.widget_in_days, 3, 3))
        assertEquals("2 Tage vorher um 20:00", q(R.plurals.reminder_day_before, 2, 2, "20:00"))
    }
}
