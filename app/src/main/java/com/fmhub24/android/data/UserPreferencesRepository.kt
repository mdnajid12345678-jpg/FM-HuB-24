package com.fmhub24.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferences: DataStore<Preferences> by preferencesDataStore(
    name = "fmhub24_preferences",
)

/** Values used by the download settings screen (spec 6.8). */
object DownloadDefaults {
    const val ASK_EACH_TIME = "ask"
    val QUALITY_OPTIONS = listOf(ASK_EACH_TIME, "360", "480", "720", "1080")
    const val DEFAULT_SIMULTANEOUS = 2
}

object SubtitleSizes {
    const val SMALL = "small"
    const val MEDIUM = "medium"
    const val LARGE = "large"
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.userPreferences

    val settings: Flow<UserSettings> = preferences.data.map { values ->
        UserSettings(
            languageTag = values[LANGUAGE],
            theme = ThemeMode.entries.firstOrNull { it.name == values[THEME] } ?: ThemeMode.DARK,
            wifiOnlyDownloads = values[WIFI_ONLY] ?: true,
            defaultQuality = values[DEFAULT_QUALITY] ?: DownloadDefaults.ASK_EACH_TIME,
            preferredAudio = values[PREFERRED_AUDIO],
            simultaneousDownloads = values[SIMULTANEOUS] ?: DownloadDefaults.DEFAULT_SIMULTANEOUS,
            deleteAfterWatching = values[DELETE_AFTER_WATCHING] ?: false,
            subtitleSize = values[SUBTITLE_SIZE] ?: SubtitleSizes.MEDIUM,
            playbackSpeed = values[PLAYBACK_SPEED] ?: 1f,
            notificationRationaleShown = values[NOTIFICATION_RATIONALE] ?: false,
            appLanguageAsked = values[LANGUAGE] != null,
        )
    }

    suspend fun saveLanguage(languageTag: String) {
        preferences.edit { it[LANGUAGE] = languageTag }
    }

    suspend fun saveTheme(theme: ThemeMode) {
        preferences.edit { it[THEME] = theme.name }
    }

    suspend fun saveWifiOnly(enabled: Boolean) {
        preferences.edit { it[WIFI_ONLY] = enabled }
    }

    suspend fun saveDefaultQuality(quality: String) {
        preferences.edit { it[DEFAULT_QUALITY] = quality }
    }

    suspend fun savePreferredAudio(language: String?) {
        preferences.edit { values ->
            if (language == null) values.remove(PREFERRED_AUDIO) else values[PREFERRED_AUDIO] = language
        }
    }

    suspend fun saveSimultaneousDownloads(count: Int) {
        preferences.edit { it[SIMULTANEOUS] = count.coerceIn(1, 5) }
    }

    suspend fun saveDeleteAfterWatching(enabled: Boolean) {
        preferences.edit { it[DELETE_AFTER_WATCHING] = enabled }
    }

    suspend fun saveSubtitleSize(size: String) {
        preferences.edit { it[SUBTITLE_SIZE] = size }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        preferences.edit { it[PLAYBACK_SPEED] = speed }
    }

    suspend fun markNotificationRationaleSeen() {
        preferences.edit { it[NOTIFICATION_RATIONALE] = true }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language_tag")
        val THEME = stringPreferencesKey("theme")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val PREFERRED_AUDIO = stringPreferencesKey("preferred_audio")
        val SIMULTANEOUS = intPreferencesKey("simultaneous_downloads")
        val DELETE_AFTER_WATCHING = booleanPreferencesKey("delete_after_watching")
        val SUBTITLE_SIZE = stringPreferencesKey("subtitle_size")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val NOTIFICATION_RATIONALE = booleanPreferencesKey("notification_rationale_shown")
    }
}

data class UserSettings(
    /** null means the first-launch language picker has not been answered yet. */
    val languageTag: String?,
    val theme: ThemeMode,
    val wifiOnlyDownloads: Boolean,
    val defaultQuality: String,
    val preferredAudio: String?,
    val simultaneousDownloads: Int,
    val deleteAfterWatching: Boolean,
    val subtitleSize: String,
    val playbackSpeed: Float,
    val notificationRationaleShown: Boolean,
    val appLanguageAsked: Boolean = languageTag != null,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
