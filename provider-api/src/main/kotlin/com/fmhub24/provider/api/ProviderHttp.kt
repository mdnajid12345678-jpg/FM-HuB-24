package com.fmhub24.provider.api

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * The small HTTP helper every provider shares. It uses HttpURLConnection on purpose: the provider
 * ABI must not depend on OkHttp or on Android classes, so the same code runs inside the app and
 * inside a plugin dex that the app loads at runtime.
 */
object ProviderHttp {
    const val USER_AGENT = "FMHuB24/1.0 (Android; provider runtime)"

    /** GET a URL and return its body as UTF-8 text. Follows redirects and decodes gzip. */
    fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 25_000,
    ): String {
        val connection = open(url, headers, timeoutMs)
        return try {
            val stream = connection.inputStream
            val decoded = if (connection.contentEncoding?.contains("gzip", true) == true) {
                GZIPInputStream(stream)
            } else {
                stream
            }
            decoded.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** GET a URL as raw bytes (used for nothing heavy today, kept for providers that need it). */
    fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int = 25_000,
    ): ByteArray {
        val connection = open(url, headers, timeoutMs)
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /** Percent-encodes a query value (space becomes %20, which both APIs accept). */
    fun encode(value: String): String = java.net.URLEncoder
        .encode(value, "UTF-8")
        .replace("+", "%20")

    /** Percent-encodes every path segment, leaving the slashes intact. */
    fun encodePath(path: String): String = path.split('/').joinToString("/") { segment ->
        encode(segment)
    }

    /** Builds a URL with query parameters, skipping blank values. */
    fun buildUrl(base: String, params: List<Pair<String, String>>): String {
        val query = params
            .filter { it.second.isNotBlank() }
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        if (query.isEmpty()) return base
        return if (base.contains('?')) "$base&$query" else "$base?$query"
    }

    /** Rejects anything that is not http(s) before the connection is opened. */
    internal fun validate(url: String) {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
        require(scheme == "http" || scheme == "https") { "Unsupported URL scheme for $url" }
    }

    private fun open(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): HttpURLConnection {
        validate(url)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Accept-Encoding", "gzip")
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) connection.setRequestProperty(key, value)
        }
        return connection
    }
}
