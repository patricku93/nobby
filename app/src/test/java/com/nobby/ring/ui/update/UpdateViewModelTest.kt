package com.nobby.ring.ui.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.update.ReleaseSource
import com.nobby.ring.update.UpdateAnnouncer
import com.nobby.ring.update.UpdateCoordinator
import com.nobby.ring.update.UpdateResult
import com.nobby.ring.update.UpdateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpdateViewModelTest {
    private class GateSource : ReleaseSource {
        var calls = 0
        val gate = CompletableDeferred<UpdateResult>()
        override suspend fun check(): UpdateResult {
            calls++
            return gate.await()
        }
    }

    private val newer = UpdateResult.Available("9.9.9", "https://github.com/p/n/releases/tag/v9.9.9", "https://github.com/p/n/a.apk")
    private lateinit var context: Context
    private lateinit var store: UpdateStore
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        store = UpdateStore(PreferenceDataStoreFactory.create { File(context.cacheDir, "vm-${System.nanoTime()}.preferences_pb") })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(source: ReleaseSource) =
        UpdateViewModel(store, UpdateCoordinator(source, store, UpdateAnnouncer { }), CoroutineScope(dispatcher))

    @Test
    fun `offer shows a newer stored release and disappears after dismissing it`() = runBlocking {
        store.recordCheck(1L, newer)
        val model = vm(GateSource())
        assertEquals(newer, withTimeout(5_000) { model.offer.first { it != null } })
        model.dismiss("9.9.9")
        assertNull(withTimeout(5_000) { model.offer.first { it == null } })
    }

    @Test
    fun `a second check while one runs does not start another`() = runBlocking {
        val source = GateSource()
        val model = vm(source)
        model.checkNow()
        model.checkNow()
        assertEquals(UpdateCheckUi.Checking, model.check.value)
        assertEquals(1, source.calls)
        source.gate.complete(UpdateResult.UpToDate)
        assertEquals(UpdateCheckUi.UpToDate, withTimeout(5_000) { model.check.first { it != UpdateCheckUi.Checking } })
    }

    @Test
    fun `a failed check shows failed and an available one carries the offer`() = runBlocking {
        val failing = GateSource().also { it.gate.complete(UpdateResult.Failed) }
        val m1 = vm(failing)
        m1.checkNow()
        assertEquals(UpdateCheckUi.Failed, withTimeout(5_000) { m1.check.first { it != UpdateCheckUi.Checking } })
        val found = GateSource().also { it.gate.complete(newer) }
        val m2 = vm(found)
        m2.checkNow()
        assertEquals(UpdateCheckUi.Available(newer), withTimeout(5_000) { m2.check.first { it != UpdateCheckUi.Checking } })
    }

    @Test
    fun `openUrl only opens trusted github links`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        activity.openUrl("https://github.com@evil.example/")
        activity.openUrl("http://github.com/p/n")
        assertNull(shadowOf(activity).nextStartedActivity)
        activity.openUrl("https://github.com/p/n/releases/tag/v1")
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://github.com/p/n/releases/tag/v1", started.dataString)
    }
}
