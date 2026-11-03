package com.nobby.ring.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.prefs.WidgetConfigStore
import com.nobby.ring.data.prefs.WidgetStyleMigration
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import com.nobby.ring.domain.model.WidgetStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WidgetConfigStoreTest {
    private val styleKey = stringPreferencesKey("widget_style")
    private val opacityKey = intPreferencesKey("widget_bg_opacity")
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: WidgetConfigStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "widget-${System.nanoTime()}.preferences_pb") }
        store = WidgetConfigStore(ds)
    }

    @Test
    fun `defaults are single, ring style and an opaque background`() {
        val d = WidgetConfig()
        assertEquals(WidgetMode.SINGLE, d.mode)
        assertEquals(WidgetStyle.RING, d.style)
        assertEquals(100, d.backgroundOpacity)
    }

    @Test
    fun `style and opacity round-trip with mode and rows`() = runTest {
        val cfg = WidgetConfig(WidgetMode.MULTI, listOf(WidgetRow.OVERDUE, WidgetRow.CYCLE_DAY), WidgetStyle.CLASSIC, 30)
        store.save(cfg)
        assertEquals(cfg, store.config.first())
        store.save(cfg.copy(style = WidgetStyle.RING, backgroundOpacity = 0))
        assertEquals(cfg.copy(style = WidgetStyle.RING, backgroundOpacity = 0), store.config.first())
    }

    @Test
    fun `missing opacity reads as 100 and stored values are snapped to 10 percent steps`() = runTest {
        ds.edit { it[stringPreferencesKey("widget_mode")] = "SINGLE" }
        assertEquals(100, store.config.first().backgroundOpacity)
        ds.edit { it[opacityKey] = 37 }
        assertEquals(40, store.config.first().backgroundOpacity)
        ds.edit { it[opacityKey] = 250 }
        assertEquals(100, store.config.first().backgroundOpacity)
        ds.edit { it[opacityKey] = -5 }
        assertEquals(0, store.config.first().backgroundOpacity)
        store.save(WidgetConfig(backgroundOpacity = 64))
        assertEquals(60, ds.data.first()[opacityKey])
    }

    @Test
    fun `unknown style name falls back to classic`() = runTest {
        ds.edit { it[styleKey] = "NEON" }
        assertEquals(WidgetStyle.CLASSIC, store.config.first().style)
    }

    @Test
    fun `a configured v2_0 widget without a style key keeps classic`() = runTest {
        ds.edit { it[stringPreferencesKey("widget_mode")] = "SINGLE" }
        assertEquals(WidgetStyle.CLASSIC, store.config.first().style)
    }

    @Test
    fun `migration gives fresh installs ring and existing installs classic`() = runTest {
        assertTrue(WidgetStyleMigration.shouldMigrate(emptyPreferences()))
        assertEquals("RING", WidgetStyleMigration.migrate(emptyPreferences())[styleKey])

        val existing = preferencesOf(stringPreferencesKey("theme_palette") to "BERRY")
        assertTrue(WidgetStyleMigration.shouldMigrate(existing))
        val migrated = WidgetStyleMigration.migrate(existing)
        assertEquals("CLASSIC", migrated[styleKey])
        assertEquals("BERRY", migrated[stringPreferencesKey("theme_palette")])

        assertFalse(WidgetStyleMigration.shouldMigrate(migrated))
    }

    @Test
    fun `a store with the migration installed reads the migrated style`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fresh = PreferenceDataStoreFactory.create(migrations = listOf(WidgetStyleMigration)) {
            File(context.cacheDir, "widget-mig-${System.nanoTime()}.preferences_pb")
        }
        assertEquals(WidgetStyle.RING, WidgetConfigStore(fresh).config.first().style)
        assertEquals(100, WidgetConfigStore(fresh).config.first().backgroundOpacity)
    }
}
