package com.fmhub24.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.data.UserPreferencesRepository
import com.fmhub24.android.data.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
) : ViewModel() {
    // null means the preference file has not been read yet; "" means follow the system language.
    val settings = repository.settings
        .map { it as UserSettings? }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun chooseLanguage(languageTag: String) {
        viewModelScope.launch { repository.saveLanguage(languageTag) }
    }

    fun chooseTheme(theme: ThemeMode) {
        viewModelScope.launch { repository.saveTheme(theme) }
    }
}
