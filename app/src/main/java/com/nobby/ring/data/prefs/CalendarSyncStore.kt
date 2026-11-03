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
    private val identityKey = stringPreferencesKey("cal_identity")
    private val lastKey = stringPreferencesKey("cal_last_sync")

    val enabled: Flow<Boolean> = dataStore.data.map { it[enabledKey] ?: false }
    val calendarId: Flow<Long?> = dataStore.data.map { it[idKey] }
    /** Identity of the chosen calendar (see DeviceCalendar.identity); null for choices stored before it existed. */
    val calendarIdentity: Flow<String?> = dataStore.data.map { it[identityKey] }
    val lastSync: Flow<LocalDateTime?> = dataStore.data.map { p -> p[lastKey]?.let { LocalDateTime.parse(it) } }

    /** A null [identity] keeps the stored one while the id is unchanged and drops it when another calendar is chosen. */
    suspend fun save(enabled: Boolean, calendarId: Long?, identity: String? = null) {
        dataStore.edit { p ->
            p[enabledKey] = enabled
            val previous = p[idKey]
            if (calendarId != null) p[idKey] = calendarId else p.remove(idKey)
            when {
                calendarId == null -> p.remove(identityKey)
                identity != null -> p[identityKey] = identity
                previous != calendarId -> p.remove(identityKey)
            }
        }
    }

    suspend fun saveIdentity(identity: String) { dataStore.edit { it[identityKey] = identity } }

    suspend fun markSynced(now: LocalDateTime) { dataStore.edit { it[lastKey] = now.toString() } }
}
