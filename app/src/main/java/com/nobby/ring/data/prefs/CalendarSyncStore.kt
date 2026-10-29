package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

class CalendarSyncStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val enabledKey = booleanPreferencesKey("cal_enabled")
    private val idKey = longPreferencesKey("cal_id")
    private val lastKey = stringPreferencesKey("cal_last_sync")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: false }
    val calendarId: Flow<Long?> = dataStore.data.map { it[idKey] }
    val lastSync: Flow<LocalDateTime?> = dataStore.data.map { p -> p[lastKey]?.let { LocalDateTime.parse(it) } }

    suspend fun save(enabled: Boolean, calendarId: Long?) {
        dataStore.edit { p ->
            p[enabledKey] = enabled
            if (calendarId != null) p[idKey] = calendarId else p.remove(idKey)
        }
    }

    suspend fun markSynced(now: LocalDateTime) { dataStore.edit { it[lastKey] = now.toString() } }
}
