package com.nobby.ring.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class UpdateCheckWorkerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun `scheduling twice keeps one daily periodic work that needs a connection`() {
        val wm = WorkManager.getInstance(context)
        UpdateCheckWorker.schedule(context)
        val first = wm.getWorkInfosForUniqueWork(UpdateCheckWorker.UNIQUE_NAME).get().single()
        UpdateCheckWorker.schedule(context)
        val info = wm.getWorkInfosForUniqueWork(UpdateCheckWorker.UNIQUE_NAME).get().single()
        assertEquals("KEEP leaves the first request alone", first.id, info.id)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.DAYS.toMillis(1), info.periodicityInfo?.repeatIntervalMillis)
    }
}
