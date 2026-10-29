package com.nobby.ring.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobby.ring.data.prefs.MethodConfigStore
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.ThemeSpec
import com.nobby.ring.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(themeStore: ThemeStore, configStore: MethodConfigStore) : ViewModel() {
    val theme: StateFlow<ThemeSpec> = themeStore.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSpec())
    val startDestination: StateFlow<String?> = configStore.onboardingDone
        .map { if (it) Routes.HOME else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
