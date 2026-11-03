package com.nobby.ring.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What the app remembers about update checks. Versions are stored normalised ("2.1.0"). */
data class UpdateState(
    val lastCheckAt: Long? = null,
    val latest: UpdateResult.Available? = null,
    val dismissedVersion: String? = null,
    val notifiedVersion: String? = null,
)

/** The banner offer: the latest seen release if it is newer than what is installed and not dismissed. */
fun selectOffer(state: UpdateState, installed: AppVersion): UpdateResult.Available? {
    val latest = state.latest ?: return null
    val version = AppVersion.parse(latest.version) ?: return null
    if (version <= installed) return null
    return latest.takeUnless { AppVersion.parse(state.dismissedVersion) == version }
}

class UpdateStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val lastCheckKey = longPreferencesKey("update_last_check_at")
    private val versionKey = stringPreferencesKey("update_latest_version")
    private val pageKey = stringPreferencesKey("update_latest_page")
    private val apkKey = stringPreferencesKey("update_latest_apk")
    private val dismissedKey = stringPreferencesKey("update_dismissed_version")
    private val notifiedKey = stringPreferencesKey("update_notified_version")

    val state: Flow<UpdateState> = dataStore.data.map { p ->
        val version = p[versionKey]
        val page = p[pageKey]
        UpdateState(
            lastCheckAt = p[lastCheckKey],
            latest = if (version != null && page != null) UpdateResult.Available(version, page, p[apkKey]) else null,
            dismissedVersion = p[dismissedKey],
            notifiedVersion = p[notifiedKey],
        )
    }

    /** Records a completed check; [latest] null means "nothing newer", which clears an earlier offer. */
    suspend fun recordCheck(at: Long, latest: UpdateResult.Available?) {
        dataStore.edit { p ->
            p[lastCheckKey] = at
            if (latest == null) {
                p.remove(versionKey)
                p.remove(pageKey)
                p.remove(apkKey)
            } else {
                p[versionKey] = latest.version
                p[pageKey] = latest.pageUrl
                if (latest.apkUrl != null) p[apkKey] = latest.apkUrl else p.remove(apkKey)
            }
        }
    }

    suspend fun dismiss(version: String) {
        dataStore.edit { it[dismissedKey] = version }
    }

    suspend fun markNotified(version: String) {
        dataStore.edit { it[notifiedKey] = version }
    }
}
