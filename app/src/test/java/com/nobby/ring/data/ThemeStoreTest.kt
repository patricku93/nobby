package com.nobby.ring.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ThemeStoreTest {
    private val paletteKey = stringPreferencesKey("theme_palette")
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: ThemeStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ds = PreferenceDataStoreFactory.create { File(context.cacheDir, "theme-${System.nanoTime()}.preferences_pb") }
        store = ThemeStore(ds)
    }

    private suspend fun storeRaw(name: String) {
        ds.edit { it[paletteKey] = name }
    }

    @Test
    fun `all five v1 palette names migrate to the v2 presets`() = runTest {
        mapOf(
            "ROSE" to Palette.BERRY,
            "LAVENDER" to Palette.LILAC,
            "MINT" to Palette.MATCHA,
            "OCEAN" to Palette.BLUEBERRY,
            "GRAPHITE" to Palette.BERRY,
        ).forEach { (old, expected) ->
            storeRaw(old)
            assertEquals(old, expected, store.theme.first().palette)
        }
    }

    @Test
    fun `missing and unknown names fall back to berry`() = runTest {
        assertEquals(Palette.BERRY, store.theme.first().palette)
        storeRaw("NEON")
        assertEquals(Palette.BERRY, store.theme.first().palette)
    }

    @Test
    fun `v2 names round-trip and saving always writes the new name`() = runTest {
        storeRaw("LAVENDER")
        val migrated = store.theme.first()
        store.save(migrated.copy(mode = ThemeMode.DARK))
        assertEquals("LILAC", ds.data.first()[paletteKey])
        store.save(ThemeSpec(Palette.PEACH, ThemeMode.LIGHT))
        assertEquals(ThemeSpec(Palette.PEACH, ThemeMode.LIGHT), store.theme.first())
    }
}
