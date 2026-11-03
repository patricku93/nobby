package com.nobby.ring.data.prefs

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import com.nobby.ring.domain.model.WidgetStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val modeKey = stringPreferencesKey("widget_mode")
private val rowsKey = stringPreferencesKey("widget_rows")
private val styleKey = stringPreferencesKey("widget_style")
private val opacityKey = intPreferencesKey("widget_bg_opacity")

class WidgetConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val config: Flow<WidgetConfig> = dataStore.data.map { p ->
        val defaults = WidgetConfig()
        WidgetConfig(
            mode = p[modeKey]?.let { runCatching { WidgetMode.valueOf(it) }.getOrNull() } ?: defaults.mode,
            rows = p[rowsKey]?.split(',')?.mapNotNull { runCatching { WidgetRow.valueOf(it) }.getOrNull() }?.ifEmpty { null }
                ?: defaults.rows,
            style = readStyle(p),
            backgroundOpacity = p[opacityKey]?.let(WidgetConfig::snapOpacity) ?: WidgetConfig.MAX_OPACITY,
        )
    }

    /**
     * [WidgetStyleMigration] writes the style once; without it (or before it ran) a widget configured under v2.0 keeps
     * Classic. An unknown name also falls back to Classic, the layout every older install knows.
     */
    private fun readStyle(p: Preferences): WidgetStyle {
        val raw = p[styleKey] ?: return if (p[modeKey] != null || p[rowsKey] != null) WidgetStyle.CLASSIC else WidgetConfig().style
        return runCatching { WidgetStyle.valueOf(raw) }.getOrDefault(WidgetStyle.CLASSIC)
    }

    suspend fun save(config: WidgetConfig) {
        dataStore.edit { p ->
            p[modeKey] = config.mode.name
            p[rowsKey] = config.rows.take(WidgetConfig.MAX_ROWS).joinToString(",") { it.name }
            p[styleKey] = config.style.name
            p[opacityKey] = WidgetConfig.snapOpacity(config.backgroundOpacity)
        }
    }
}

/**
 * Runs once, on the first read of the app's preferences after installing v2.1: an empty store is a fresh install and
 * gets the Ring style; a store with any v2.0 data (theme, method, widget, ...) is an update and keeps Classic.
 */
object WidgetStyleMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[styleKey] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val style = if (currentData.asMap().isEmpty()) WidgetStyle.RING else WidgetStyle.CLASSIC
        return currentData.toMutablePreferences().apply { this[styleKey] = style.name }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
