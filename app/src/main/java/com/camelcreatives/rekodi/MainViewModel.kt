package com.camelcreatives.rekodi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.camelcreatives.rekodi.data.datastore.SettingsDataStore
import com.camelcreatives.rekodi.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val startDestination: StateFlow<String?> = settingsDataStore.settings
        .map { if (it.onboardingCompleted) Routes.HOME else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.updateOnboardingCompleted(true)
        }
    }
}
