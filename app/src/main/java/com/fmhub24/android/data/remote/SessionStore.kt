package com.fmhub24.android.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fmhub_session",
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long,
) {
    fun isExpiring(withinMillis: Long = 60_000L): Boolean =
        expiresAtEpochMs <= System.currentTimeMillis() + withinMillis
}

/**
 * Stores the anonymous Supabase session on the phone. The app never signs a user in with a
 * password; anonymous sign-in only exists so the read policies for the authenticated role pass.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.sessionDataStore

    val session: Flow<AuthSession?> = store.data.map { values ->
        val access = values[ACCESS]
        if (access.isNullOrBlank()) {
            null
        } else {
            AuthSession(
                accessToken = access,
                refreshToken = values[REFRESH],
                expiresAtEpochMs = values[EXPIRES] ?: 0L,
            )
        }
    }

    suspend fun current(): AuthSession? = session.first()

    suspend fun save(session: AuthSession) {
        store.edit { values ->
            values[ACCESS] = session.accessToken
            values[REFRESH] = session.refreshToken ?: ""
            values[EXPIRES] = session.expiresAtEpochMs
        }
    }

    suspend fun clear() {
        store.edit { values ->
            values.remove(ACCESS)
            values.remove(REFRESH)
            values.remove(EXPIRES)
        }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES = longPreferencesKey("expires_at")
    }
}
