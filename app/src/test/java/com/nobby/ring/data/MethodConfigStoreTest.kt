package com.nobby.ring.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.prefs.MethodConfigStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MethodConfigStoreTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: MethodConfigStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dataStore = PreferenceDataStoreFactory.create { File(context.cacheDir, "test-${System.nanoTime()}.preferences_pb") }
        store = MethodConfigStore(dataStore)
    }

    @Test
    fun `out-of-range raw preferences are coerced without throwing`() = runTest {
        dataStore.edit { p ->
            p[stringPreferencesKey("anchor")] = "2026-11-01T21:00"
            p[intPreferencesKey("wear_days")] = 99
            p[intPreferencesKey("pause_days")] = -3
            p[intPreferencesKey("horizon_months")] = 0
            p[intPreferencesKey("time_hour")] = 30
        }
        val config = store.config.first()
        assertNotNull(config)
        assertEquals(35, config!!.method.wearDays)
        assertEquals(0, config.method.pauseDays)
        assertEquals(1, config.horizonMonths)
        assertEquals(23, config.method.timeOfDay.hour)
        assertEquals(LocalDateTime(2026, 11, 1, 21, 0), config.anchor)
    }

    @Test
    fun `unparseable anchor yields null config`() = runTest {
        dataStore.edit { it[stringPreferencesKey("anchor")] = "garbage" }
        assertNull(store.config.first())
    }
}
