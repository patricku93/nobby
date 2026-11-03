package com.nobby.ring.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobby.ring.domain.model.Method
import com.nobby.ring.domain.model.ScheduleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import javax.inject.Inject

class MethodConfigStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val wear = intPreferencesKey("wear_days")
        val pause = intPreferencesKey("pause_days")
        val hour = intPreferencesKey("time_hour")
        val minute = intPreferencesKey("time_minute")
        val anchor = stringPreferencesKey("anchor")
        val horizon = intPreferencesKey("horizon_months")
        val skipPause = booleanPreferencesKey("skip_pause_once")
        val firstInsertPending = booleanPreferencesKey("first_insert_pending")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    /** Persisted values are coerced into the ranges [ScheduleConfig] accepts, so corrupt data cannot crash on load. */
    val config: Flow<ScheduleConfig?> = dataStore.data.map { p ->
        val anchor = p[Keys.anchor]
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?: return@map null
        ScheduleConfig(
            method = Method.ring(
                wearDays = (p[Keys.wear] ?: 21).coerceIn(ScheduleConfig.MIN_WEAR, ScheduleConfig.MAX_WEAR),
                pauseDays = (p[Keys.pause] ?: 7).coerceIn(ScheduleConfig.MIN_PAUSE, ScheduleConfig.MAX_PAUSE),
                timeOfDay = LocalTime((p[Keys.hour] ?: 21).coerceIn(0, 23), (p[Keys.minute] ?: 0).coerceIn(0, 59)),
            ),
            anchor = anchor,
            horizonMonths = (p[Keys.horizon] ?: 12).coerceIn(ScheduleConfig.MIN_HORIZON, ScheduleConfig.MAX_HORIZON),
            skipPauseOnce = p[Keys.skipPause] ?: false,
            firstInsertPending = p[Keys.firstInsertPending] ?: false,
        )
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[Keys.onboardingDone] ?: false }

    suspend fun save(config: ScheduleConfig) {
        dataStore.edit { p ->
            p[Keys.wear] = config.method.wearDays
            p[Keys.pause] = config.method.pauseDays
            p[Keys.hour] = config.method.timeOfDay.hour
            p[Keys.minute] = config.method.timeOfDay.minute
            p[Keys.anchor] = config.anchor.toString()
            p[Keys.horizon] = config.horizonMonths
            p[Keys.skipPause] = config.skipPauseOnce
            p[Keys.firstInsertPending] = config.firstInsertPending
        }
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[Keys.onboardingDone] = true }
    }
}
