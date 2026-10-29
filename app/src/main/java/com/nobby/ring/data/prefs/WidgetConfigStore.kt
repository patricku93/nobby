package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.WidgetConfig
import com.nobby.ring.domain.model.WidgetMode
import com.nobby.ring.domain.model.WidgetRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WidgetConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val modeKey = stringPreferencesKey("widget_mode")
    private val rowsKey = stringPreferencesKey("widget_rows")

    val config: Flow<WidgetConfig> = dataStore.data.map { p ->
        val defaults = WidgetConfig()
        WidgetConfig(
            mode = p[modeKey]?.let { runCatching { WidgetMode.valueOf(it) }.getOrNull() } ?: defaults.mode,
            rows = p[rowsKey]?.split(',')?.mapNotNull { runCatching { WidgetRow.valueOf(it) }.getOrNull() }?.ifEmpty { null }
                ?: defaults.rows,
        )
    }

    suspend fun save(config: WidgetConfig) {
        dataStore.edit { p ->
            p[modeKey] = config.mode.name
            p[rowsKey] = config.rows.take(WidgetConfig.MAX_ROWS).joinToString(",") { it.name }
        }
    }
}
