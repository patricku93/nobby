package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.domain.model.ThemeMode
import com.nobby.ring.domain.model.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ThemeStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val paletteKey = stringPreferencesKey("theme_palette")
    private val modeKey = stringPreferencesKey("theme_mode")

    val theme: Flow<ThemeSpec> = dataStore.data.map { p ->
        ThemeSpec(
            palette = p[paletteKey]?.let { runCatching { Palette.valueOf(it) }.getOrNull() } ?: Palette.ROSE,
            mode = p[modeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun save(theme: ThemeSpec) {
        dataStore.edit { p ->
            p[paletteKey] = theme.palette.name
            p[modeKey] = theme.mode.name
        }
    }
}
