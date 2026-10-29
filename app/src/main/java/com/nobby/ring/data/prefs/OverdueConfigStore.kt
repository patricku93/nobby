package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.nobby.ring.domain.model.OverdueConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OverdueConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val repeatKey = intPreferencesKey("overdue_repeat_hours")
    private val snoozeKey = intPreferencesKey("overdue_snooze_hours")

    val config: Flow<OverdueConfig> = dataStore.data.map { p ->
        OverdueConfig(p[repeatKey] ?: 2, p[snoozeKey] ?: 1)
    }

    suspend fun save(config: OverdueConfig) {
        dataStore.edit { p ->
            p[repeatKey] = config.repeatEveryHours
            p[snoozeKey] = config.snoozeHours
        }
    }
}
