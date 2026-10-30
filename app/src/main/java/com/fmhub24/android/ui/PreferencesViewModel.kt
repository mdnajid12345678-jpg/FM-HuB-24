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

    fun chooseLanguage(languageTag: String) = launch { repository.saveLanguage(languageTag) }

    fun chooseTheme(theme: ThemeMode) = launch { repository.saveTheme(theme) }

    fun setWifiOnly(enabled: Boolean) = launch { repository.saveWifiOnly(enabled) }

    fun setDefaultQuality(quality: String) = launch { repository.saveDefaultQuality(quality) }

    fun setPreferredAudio(language: String?) = launch { repository.savePreferredAudio(language) }

    fun setSimultaneousDownloads(count: Int) = launch { repository.saveSimultaneousDownloads(count) }

    fun setDeleteAfterWatching(enabled: Boolean) = launch {
        repository.saveDeleteAfterWatching(enabled)
    }

    fun setSubtitleSize(size: String) = launch { repository.saveSubtitleSize(size) }

    fun setPlaybackSpeed(speed: Float) = launch { repository.savePlaybackSpeed(speed) }

    fun markNotificationRationaleSeen() = launch { repository.markNotificationRationaleSeen() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
