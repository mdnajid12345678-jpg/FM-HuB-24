package com.fmhub24.android.data.remote

import com.fmhub24.android.BuildConfig
import com.fmhub24.android.data.model.DataException
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Thin PostgREST + GoTrue client. Only the project URL and the publishable (anon) key are used;
 * a service_role key must never appear here (spec section 2).
 *
 * Reads go through the anonymous session so the read-only RLS policies for the authenticated
 * role pass. Every failure is surfaced as a [DataException] instead of being swallowed.
 */
@Singleton
class SupabaseRest @Inject constructor(
    private val http: OkHttpClient,
    private val sessionStore: SessionStore,
) {
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    /** GET one table as JSON rows. [query] holds PostgREST query parameters. */
    suspend fun select(
        table: String,
        query: List<Pair<String, String>> = emptyList(),
        allowRetry: Boolean = true,
    ): List<JSONObject> = withContext(Dispatchers.IO) {
        ensureConfigured()
        val body = execute("GET", restUrl(table, query), allowRetry)
        if (body.isBlank()) return@withContext emptyList()
        try {
            val array = org.json.JSONArray(body)
            buildList {
                for (i in 0 until array.length()) {
                    val element = array.opt(i)
                    if (element is JSONObject) add(element)
                }
            }
        } catch (error: Exception) {
            throw DataException.Malformed(error.message ?: "rows")
        }
    }

    /** Reads a single row, or null when the result set is empty. */
    suspend fun selectFirst(
        table: String,
        query: List<Pair<String, String>> = emptyList(),
    ): JSONObject? = select(table, query).firstOrNull()

    // ---------------------------------------------------------------------------------------------
    // Anonymous auth
    // ---------------------------------------------------------------------------------------------

    private suspend fun anonymousSession(): AuthSession {
        val url = "$baseUrl/auth/v1/signup".toHttpUrl().newBuilder().build()
        val payload = JSONObject().put("data", JSONObject()).toString()
        val body = execute(
            method = "POST",
            url = url,
            allowRetry = false,
            authOverride = anonKey,
            requestBody = payload,
            path = "signup",
        )
        return parseSession(body)
    }

    private suspend fun refreshSession(refreshToken: String): AuthSession {
        val url = "$baseUrl/auth/v1/token?grant_type=refresh_token".toHttpUrl()
        val payload = JSONObject().put("refresh_token", refreshToken).toString()
        val body = execute(
            method = "POST",
            url = url,
            allowRetry = false,
            authOverride = anonKey,
            requestBody = payload,
            path = "token",
        )
        return parseSession(body)
    }

    private fun parseSession(body: String): AuthSession {
        try {
            val json = JSONObject(body)
            val access = json.optString("access_token")
            if (access.isBlank()) throw DataException.AuthFailed("no access_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            return AuthSession(
                accessToken = access,
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L,
            )
        } catch (error: DataException) {
            throw error
        } catch (error: Exception) {
            throw DataException.AuthFailed(error.message ?: "session")
        }
    }

    private suspend fun validSession(): AuthSession {
        sessionStore.current()?.let { existing ->
            if (!existing.isExpiring()) return existing
            existing.refreshToken?.let { refreshToken ->
                try {
                    val refreshed = refreshSession(refreshToken)
                    sessionStore.save(refreshed)
                    return refreshed
                } catch (_: DataException) {
                    sessionStore.clear()
                }
            }
        }
        val created = anonymousSession()
        sessionStore.save(created)
        return created
    }

    // ---------------------------------------------------------------------------------------------

    private fun restUrl(table: String, query: List<Pair<String, String>>): HttpUrl {
        val builder = "$baseUrl/rest/v1/$table".toHttpUrl().newBuilder()
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun ensureConfigured() {
        if (!isConfigured) throw DataException.NotConfigured()
    }

    /**
     * Runs one request. On 401 the session is refreshed once and the request repeated; a 401/402/403
     * from PostgREST means RLS or a missing policy, which the UI reports verbatim (spec 5.1).
     */
    private suspend fun execute(
        method: String,
        url: HttpUrl,
        allowRetry: Boolean,
        authOverride: String? = null,
        requestBody: String? = null,
        path: String? = null,
        attempt: Int = 0,
    ): String {
        val token = authOverride ?: validSession().accessToken
        val mediaType = "application/json".toMediaType()
        val requestBuilder = Request.Builder().url(url)
        requestBuilder.header("apikey", anonKey)
        requestBuilder.header("Authorization", "Bearer $token")
        requestBuilder.header("Accept", "application/json")
        requestBuilder.header("Accept-Profile", "public")
        if (path != null) requestBuilder.header("X-Client-Info", "fmhub24-android")
        if (requestBody != null) {
            requestBuilder.post(requestBody.toRequestBody(mediaType))
        }

        val response = try {
            http.newCall(requestBuilder.build()).execute()
        } catch (error: IOException) {
            throw DataException.Offline(error)
        }

        response.use { body ->
            val text = body.body?.string().orEmpty()
            when {
                body.isSuccessful -> return text
                body.code == 401 && authOverride == null && allowRetry && attempt == 0 -> {
                    sessionStore.clear()
                    return execute(method, url, allowRetry, authOverride, requestBody, path, attempt + 1)
                }
                body.code == 401 || body.code == 403 -> {
                    // Almost always an RLS policy refusing the read: stop, never try to bypass it.
                    if (path == "signup" || path == "token") {
                        throw DataException.AuthFailed(errorDetail(text))
                    }
                    throw DataException.PermissionBlocked()
                }
                body.code == 400 && (path == "signup" || path == "token") -> {
                    throw DataException.AuthFailed(errorDetail(text))
                }
                else -> throw DataException.Http(body.code, errorDetail(text))
            }
        }
    }

    private fun errorDetail(text: String): String = try {
        val json = JSONObject(text)
        json.optString("error_description")
            .ifBlank { json.optString("msg") }
            .ifBlank { json.optString("message") }
            .ifBlank { text.take(200) }
    } catch (_: Exception) {
        text.take(200)
    }

    companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
