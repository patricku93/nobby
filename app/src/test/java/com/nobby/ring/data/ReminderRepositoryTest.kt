package com.nobby.ring.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.repo.ReminderRepository
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.ReminderRule
import com.nobby.ring.domain.model.ReminderTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderRepositoryTest {
    private lateinit var db: NobbyDatabase
    private lateinit var repo: ReminderRepository

    private fun rule(offsetDays: Int, id: Long = 0) =
        ReminderRule(id, ReminderTarget.ANY, offsetDays, LocalTime(20, 0), null, Importance.NORMAL, true)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NobbyDatabase::class.java).allowMainThreadQueries().build()
        repo = ReminderRepository(db.reminderRuleDao())
    }

    @After fun tearDown() = db.close()

    @Test
    fun `upsert returns the existing id on update`() = runTest {
        val id = repo.upsert(rule(1))
        assertTrue(id > 0)
        val updatedId = repo.upsert(rule(1, id).copy(enabled = false))
        assertEquals(id, updatedId)
        val rules = repo.rules.first()
        assertEquals(1, rules.size)
        assertFalse(rules.single().enabled)
    }

    @Test
    fun `replaceAll replaces atomically`() = runTest {
        repo.upsert(rule(1))
        repo.upsert(rule(2))
        repo.replaceAll(listOf(rule(3, id = 99)))
        val rules = repo.rules.first()
        assertEquals(1, rules.size)
        assertEquals(3, rules.single().offsetDays)
    }
}
