@file:OptIn(UnstableApi::class)

package com.fmhub24.android.stream

import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import com.fmhub24.android.data.model.MediaSource
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONObject

/** Honest failure categories shown to the user (spec section 8). */
enum class StreamError {
    /** The signed link is gone: HTTP 403/404 or an HLS segment/manifest failure. */
    EXPIRED,

    /** No connectivity at all. */
    OFFLINE,

    /** Anything else: the video could not be played, offer other servers. */
    FAILED,
}

/**
 * One HTTP data source factory built from the chosen source's headers, used by the player AND by
 * downloads so every manifest and segment request carries them (spec section 7).
 */
object StreamEngine {
    const val CONNECT_TIMEOUT_MS = 20_000
    const val READ_TIMEOUT_MS = 30_000

    fun dataSourceFactory(headers: Map<String, String>): DataSource.Factory {
        val factory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) {
            factory.setDefaultRequestProperties(headers.toMap())
        }
        return factory
    }

    fun mimeTypeFor(source: MediaSource): String? {
        val path = source.mediaUrl.substringBefore("?").substringBefore("#").lowercase()
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            source.isHls -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".ts") -> "video/mp2t"
            else -> if (source.isDirectVideo) "video/mp4" else null
        }
    }

    fun mediaItem(source: MediaSource, key: String): Media3MediaItem =
        Media3MediaItem.Builder()
            .setMediaId(key)
            .setUri(source.mediaUrl)
            .setMimeType(mimeTypeFor(source))
            .build()

    fun encodeHeaders(headers: Map<String, String>): ByteArray =
        JSONObject().apply { headers.forEach { (name, value) -> put(name, value) } }
            .toString().toByteArray(Charsets.UTF_8)

    fun decodeHeaders(data: ByteArray?): Map<String, String> {
        if (data == null || data.isEmpty()) return emptyMap()
        return runCatching {
            val json = JSONObject(String(data, Charsets.UTF_8))
            buildMap {
                json.keys().forEach { key ->
                    val value = json.opt(key)?.toString()
                    if (!value.isNullOrBlank()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * Maps an exception to the messages of section 8: expired link, offline, or a failure that
     * offers the other servers.
     */
    fun classify(throwable: Throwable): StreamError {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            when (current) {
                is HttpDataSource.InvalidResponseCodeException -> {
                    return when (current.responseCode) {
                        401, 403, 404, 410 -> StreamError.EXPIRED
                        else -> StreamError.FAILED
                    }
                }
                is UnknownHostException, is ConnectException, is NoRouteToHostException,
                is SocketTimeoutException, is SSLException,
                -> return StreamError.OFFLINE
                is IOException -> {
                    // A playlist or segment read that died mid-stream is an expired link.
                    if (current.message.orEmpty().contains("403", ignoreCase = true) ||
                        current.message.orEmpty().contains("404", ignoreCase = true)
                    ) {
                        return StreamError.EXPIRED
                    }
                }
            }
            current = current.cause
            depth++
        }
        return StreamError.FAILED
    }

    /** Audio/video track types used when reading the stream's own track groups. */
    val videoTrackType: Int get() = C.TRACK_TYPE_VIDEO
    val audioTrackType: Int get() = C.TRACK_TYPE_AUDIO
    val textTrackType: Int get() = C.TRACK_TYPE_TEXT
}
