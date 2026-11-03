package com.nobby.ring.update

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.notifications.Channels
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UpdateNotifierTest {
    private lateinit var context: Context
    private val offer = UpdateResult.Available("2.1.0", "https://github.com/p/n/releases/tag/v2.1.0", null)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Channels.create(context)
    }

    @Test
    fun `posts one low importance notification on the updates channel that opens the release page`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        UpdateNotifier(context).announce(offer)
        val posted = shadowOf(nm).allNotifications
        assertEquals(1, posted.size)
        assertEquals(Channels.UPDATES, posted[0].channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, nm.getNotificationChannel(Channels.UPDATES).importance)
        val intent = shadowOf(posted[0].contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(offer.pageUrl, intent.dataString)
    }

    @Test
    fun `announcing again replaces instead of stacking`() {
        val nm = context.getSystemService(NotificationManager::class.java)
        UpdateNotifier(context).announce(offer)
        UpdateNotifier(context).announce(offer.copy(version = "2.2.0"))
        assertEquals(1, shadowOf(nm).allNotifications.size)
    }
}
