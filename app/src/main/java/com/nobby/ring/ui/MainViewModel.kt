package com.nobby.ring.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.notifications.ReminderScheduler
import com.nobby.ring.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    themeStore: ThemeStore,
    configStore: MethodConfigStore,
    scheduler: ReminderScheduler,
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {
    val theme: StateFlow<ThemeSpec> = themeStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSpec())
    val startDestination: StateFlow<String?> = flow { emit(if (configStore.onboardingDone.first()) Routes.HOME else Routes.ONBOARDING) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Alarms can be lost without a broadcast (force-stop, OEM task killers, a missed TIME_SET): re-arm on every start.
        appScope.launch {
            try {
                scheduler.rescheduleAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NoBby", "reschedule on app start failed", e)
            }
        }
    }
}
