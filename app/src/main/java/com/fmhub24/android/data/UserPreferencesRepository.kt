package com.fmhub24.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.userPreferences

    val settings: Flow<UserSettings> = preferences.data.map { values ->
        UserSettings(
            languageTag = values[LANGUAGE],
            theme = ThemeMode.entries.firstOrNull { it.name == values[THEME] }
                ?: ThemeMode.DARK,
        )
    }

    suspend fun saveLanguage(languageTag: String) {
        preferences.edit { it[LANGUAGE] = languageTag }
    }

    suspend fun saveTheme(theme: ThemeMode) {
        preferences.edit { it[THEME] = theme.name }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language_tag")
        val THEME = stringPreferencesKey("theme")
    }
}

data class UserSettings(
    val languageTag: String?,
    val theme: ThemeMode,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
