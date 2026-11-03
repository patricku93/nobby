package com.nobby.ring.update

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateCoordinatorTest {
    private class FakeSource(var result: UpdateResult) : ReleaseSource {
        var calls = 0
        override suspend fun check(): UpdateResult {
            calls++
            return result
        }
    }

    private val hour = 3_600_000L
    private val v21 = UpdateResult.Available("2.1.0", "https://github.com/p/n/releases/tag/v2.1.0", "https://github.com/p/n/a.apk")
    private val v22 = UpdateResult.Available("2.2.0", "https://github.com/p/n/releases/tag/v2.2.0", null)
    private val current = AppVersion(2, 0, 0)

    private lateinit var store: UpdateStore
    private val announced = mutableListOf<UpdateResult.Available>()
    private var clockMs = 1_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        store = UpdateStore(PreferenceDataStoreFactory.create { File(context.cacheDir, "update-${System.nanoTime()}.preferences_pb") })
        announced.clear()
    }

    private fun coordinator(source: ReleaseSource) = UpdateCoordinator(source, store, { announced += it }, { clockMs })

    @Test
    fun `first check runs and later ones within 24 hours are skipped`() = runTest {
        val src = FakeSource(UpdateResult.UpToDate)
        val c = coordinator(src)
        assertEquals(UpdateResult.UpToDate, c.checkIfDue())
        clockMs += 24 * hour - 1
        assertNull(c.checkIfDue())
        assertEquals(1, src.calls)
        clockMs += 1
        assertEquals(UpdateResult.UpToDate, c.checkIfDue())
        assertEquals(2, src.calls)
    }

    @Test
    fun `a failed check does not start the 24 hour window`() = runTest {
        val src = FakeSource(UpdateResult.Failed)
        val c = coordinator(src)
        c.checkIfDue()
        c.checkIfDue()
        assertEquals(2, src.calls)
    }

    @Test
    fun `manual check ignores the gate but restarts the window`() = runTest {
        val src = FakeSource(UpdateResult.UpToDate)
        val c = coordinator(src)
        c.checkNow()
        c.checkNow()
        assertEquals(2, src.calls)
        assertNull(c.checkIfDue())
    }

    @Test
    fun `an available release is stored and offered`() = runTest {
        coordinator(FakeSource(v21)).checkIfDue()
        assertEquals(v21, selectOffer(store.state.first(), current))
    }

    @Test
    fun `dismissing a version hides its banner but not a newer one`() = runTest {
        val src = FakeSource(v21)
        val c = coordinator(src)
        c.checkNow()
        store.dismiss("2.1.0")
        assertNull(selectOffer(store.state.first(), current))
        src.result = v22
        c.checkNow()
        assertEquals(v22, selectOffer(store.state.first(), current))
    }

    @Test
    fun `no offer once the installed version has caught up`() = runTest {
        coordinator(FakeSource(v21)).checkNow()
        assertNull(selectOffer(store.state.first(), AppVersion(2, 1, 0)))
        assertNull(selectOffer(store.state.first(), AppVersion(2, 5, 0)))
    }

    @Test
    fun `up to date clears a stored offer`() = runTest {
        val src = FakeSource(v21)
        val c = coordinator(src)
        c.checkNow()
        src.result = UpdateResult.UpToDate
        c.checkNow()
        assertNull(selectOffer(store.state.first(), current))
    }

    @Test
    fun `failure keeps the previously seen offer`() = runTest {
        val src = FakeSource(v21)
        val c = coordinator(src)
        c.checkNow()
        src.result = UpdateResult.Failed
        c.checkNow()
        assertEquals(v21, selectOffer(store.state.first(), current))
    }

    @Test
    fun `a version is announced once even across checks and dismissal`() = runTest {
        val src = FakeSource(v21)
        val c = coordinator(src)
        c.checkNow()
        c.checkNow()
        store.dismiss("2.1.0")
        c.checkNow()
        assertEquals(listOf(v21), announced)
        src.result = v22
        c.checkNow()
        c.checkNow()
        assertEquals(listOf(v21, v22), announced)
    }

    @Test
    fun `up to date and failed never announce`() = runTest {
        coordinator(FakeSource(UpdateResult.UpToDate)).checkNow()
        coordinator(FakeSource(UpdateResult.Failed)).checkNow()
        assertEquals(emptyList<UpdateResult.Available>(), announced)
    }

    @Test
    fun `a throwing announcer does not break the check`() = runTest {
        val c = UpdateCoordinator(FakeSource(v21), store, { error("no notification") }, { clockMs })
        assertEquals(v21, c.checkNow())
        assertEquals(v21, selectOffer(store.state.first(), current))
    }

    @Test
    fun `untrusted links read back from the store are dropped`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "raw-${System.nanoTime()}.preferences_pb") }
        val raw = UpdateStore(ds)
        ds.edit {
            it[stringPreferencesKey("update_latest_version")] = "2.1.0"
            it[stringPreferencesKey("update_latest_page")] = "https://github.com/p/n/releases/tag/v2.1.0"
            it[stringPreferencesKey("update_latest_apk")] = "https://github.com@evil.example/a.apk"
        }
        assertEquals(UpdateResult.Available("2.1.0", "https://github.com/p/n/releases/tag/v2.1.0", null), raw.state.first().latest)
        ds.edit { it[stringPreferencesKey("update_latest_page")] = "https://evil.example/github.com" }
        assertNull(raw.state.first().latest)
    }
}
