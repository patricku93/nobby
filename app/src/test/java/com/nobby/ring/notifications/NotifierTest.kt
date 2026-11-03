package com.nobby.ring.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.R
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.mascotColors
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotifierTest {
    private lateinit var context: Context
    private lateinit var themeStore: ThemeStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Channels.create(context)
        themeStore = ThemeStore(PreferenceDataStoreFactory.create { File(context.cacheDir, "notif-${System.nanoTime()}.preferences_pb") })
    }

    @Test
    fun `large icons exist for every preset and mood and are cached`() {
        val px = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        for (p in Palette.entries) for (m in listOf(MascotMood.HAPPY, MascotMood.WORRIED)) {
            val bmp = NotificationIcons.large(context, p, m)
            assertEquals(px, bmp.width)
            assertSame(bmp, NotificationIcons.large(context, p, m))
        }
    }

    @Test
    fun `large icons show the preset body colour on a transparent corner`() {
        for (p in Palette.entries) {
            val bmp = NotificationIcons.large(context, p, MascotMood.HAPPY)
            val body = p.mascotColors(dark = false).body.toArgb()
            // Left of the centre line, clear of the eyes and mouth, inside the body disc.
            assertEquals("body colour for ${p.name}", body, bmp.getPixel(bmp.width / 5 * 2, bmp.height / 2))
            assertEquals("corner transparent for ${p.name}", 0, bmp.getPixel(0, 0) ushr 24)
        }
    }

    @Test
    fun `reminder uses the ring glyph and a preset face`() = runTest {
        themeStore.save(ThemeSpec(Palette.MATCHA))
        Notifier(context, themeStore).showReminder(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), Importance.NORMAL, "REMOVE@2026-11-22T21:00")
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_nobby, n.smallIcon.resId)
        assertNotNull(n.getLargeIcon())
    }

    @Test
    fun `overdue uses the ring glyph and a large icon`() = runTest {
        Notifier(context, themeStore).showOverdue(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), "x", safetyBreached = true)
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_nobby, n.smallIcon.resId)
        assertNotNull(n.getLargeIcon())
    }

    private fun failingNotifier() = Notifier(context, themeStore) { _, _, _ -> throw IllegalStateException("painter failed") }

    @Test
    fun `overdue still posts with the small icon when the large icon render fails`() = runTest {
        failingNotifier().showOverdue(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), "x", safetyBreached = true)
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_nobby, n.smallIcon.resId)
        assertNull(n.getLargeIcon())
        assertEquals(2, n.actions.size)
    }

    @Test
    fun `reminder still posts with the small icon when the large icon render fails`() = runTest {
        failingNotifier().showReminder(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), Importance.NORMAL, "REMOVE@2026-11-22T21:00")
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_nobby, n.smallIcon.resId)
        assertNull(n.getLargeIcon())
    }

    @Test
    fun `out of memory while rendering the icon does not stop the notification`() = runTest {
        Notifier(context, themeStore) { _, _, _ -> throw OutOfMemoryError("bitmap") }
            .showOverdue(EventKind.REMOVE, LocalDateTime(2026, 11, 22, 21, 0), "x", safetyBreached = false)
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_nobby, n.smallIcon.resId)
        assertNull(n.getLargeIcon())
    }
}
