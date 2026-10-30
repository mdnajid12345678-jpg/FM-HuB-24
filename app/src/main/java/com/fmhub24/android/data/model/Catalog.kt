package com.fmhub24.android.data.model

import org.json.JSONObject

/** How a title behaves in the UI: single item or seasons + episodes (spec 5.2 rule 7). */
enum class ContentType(val raw: String, val usesEpisodes: Boolean) {
    MOVIE("movie", false),
    ANIME_MOVIE("anime_movie", false),
    SERIES("series", true),
    ANIME_SERIES("anime_series", true),
    OVA("ova", true),
    CARTOON("cartoon", true),
    ASIAN_DRAMA("asian_drama", true),
    DOCUMENTARY("documentary", false),
    LIVE("live", false),
    OTHER("other", false);

    companion object {
        fun from(raw: String?): ContentType =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: OTHER
    }
}

/** Home category chips (spec 5.3). Documentary and other only appear under All. */
enum class HomeCategory { ALL, MOVIES, SERIES, ANIME }

fun ContentType.inCategory(category: HomeCategory): Boolean = when (category) {
    HomeCategory.ALL -> true
    HomeCategory.MOVIES -> this == ContentType.MOVIE || this == ContentType.ANIME_MOVIE
    HomeCategory.SERIES ->
        this == ContentType.SERIES || this == ContentType.ASIAN_DRAMA || this == ContentType.CARTOON
    HomeCategory.ANIME ->
        this == ContentType.ANIME_SERIES || this == ContentType.ANIME_MOVIE || this == ContentType.OVA
}

data class MediaItem(
    val id: String,
    val canonicalKey: String?,
    val title: String,
    val originalTitle: String?,
    val contentType: ContentType,
    val releaseYear: Int?,
    val posterUrl: String?,
    val description: String?,
    val genres: List<String>,
    val status: String?,
    val rating: Double?,
    val createdAt: String?,
    val updatedAt: String?,
) {
    val usesEpisodes: Boolean get() = contentType.usesEpisodes
    val isLive: Boolean get() = contentType == ContentType.LIVE

    /** Spec 5.3: same content_type and an overlapping genre. */
    fun isSimilarTo(other: MediaItem): Boolean =
        id != other.id && contentType == other.contentType &&
            genres.any { other.genres.contains(it) }
}

data class SeasonItem(
    val contentId: String,
    val seasonNumber: Int,
    val name: String?,
    val sortOrder: Int?,
) {
    val storageKey: String get() = "$contentId|$seasonNumber"
}

data class EpisodeItem(
    /** Database id when the table has one; null otherwise. */
    val id: String?,
    val contentId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val description: String?,
    val posterUrl: String?,
    val sortOrder: Int?,
) {
    /** Stable identity used for history, downloads and player routes. */
    val key: String get() = "$contentId|$seasonNumber|$episodeNumber"

    fun matchesSource(source: MediaSource): Boolean {
        val sourceEpisodeId = source.episodeId
        if (!sourceEpisodeId.isNullOrBlank()) {
            return sourceEpisodeId == id || sourceEpisodeId == key
        }
        if (source.seasonNumber != null && source.episodeNumber != null) {
            return source.seasonNumber == seasonNumber && source.episodeNumber == episodeNumber
        }
        return false
    }
}

/**
 * A row from media_sources. media_url is a short-lived signed link: it is used only at playback
 * time and is never shown or shared (spec 5.2 rules 3 and 10).
 */
data class MediaSource(
    val id: String?,
    val contentId: String?,
    val episodeId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val serverName: String,
    val mediaUrl: String,
    val quality: Int?,
    val linkType: String?,
    val status: String?,
    val headers: Map<String, String>,
    val columnLanguage: String?,
    val columnIsDub: Boolean?,
    val columnCodec: String?,
    val resolvedAt: String?,
    val expiresAt: String?,
) {
    /** Spec 5.2 rules 1 and 2: only resolved rows, never provider_payload. */
    val isPlayable: Boolean
        get() = status.equals("resolved", ignoreCase = true) &&
            mediaUrl.isNotBlank() &&
            !linkType.equals("provider_payload", ignoreCase = true)

    val isDirectVideo: Boolean get() = linkType.equals("VIDEO", ignoreCase = true)
    val isHls: Boolean get() = linkType.equals("M3U8", ignoreCase = true)

    /** Text before the bullet, or the first word (spec 5.4). */
    val serverLabel: String
        get() = run {
            val beforeBullet = serverName.substringBefore("•").trim()
            if (beforeBullet.isNotEmpty()) beforeBullet
            else serverName.trim().substringBefore(" ")
        }.ifEmpty { serverName }

    val codec: String?
        get() = columnCodec ?: SourceMeta.codecFromText(serverName)

    val language: String?
        get() = columnLanguage ?: SourceMeta.languageFromText(serverName)

    val isDub: Boolean
        get() = columnIsDub ?: serverName.contains("Dub", ignoreCase = true)

    /** Short label shown in lists, e.g. "H264" or "HEVC". */
    val codecLabel: String? get() = codec?.let { SourceMeta.codecLabel(it) }
}

object SourceMeta {
    private val languageTokens = listOf(
        "Multi-Lang" to "Multi",
        "Multi" to "Multi",
        "Original" to "Original",
        "Bengali" to "Bengali",
        "Bangla" to "Bengali",
        "Hindi" to "Hindi",
        "Telugu" to "Telugu",
        "Tamil" to "Tamil",
        "English" to "English",
    )

    fun codecFromText(text: String): String? = when {
        text.contains("AV1", ignoreCase = true) -> "AV1"
        text.contains("HEVC", ignoreCase = true) ||
            text.contains("H265", ignoreCase = true) ||
            text.contains("H.265", ignoreCase = true) -> "HEVC"
        text.contains("H264", ignoreCase = true) ||
            text.contains("H.264", ignoreCase = true) ||
            text.contains("AVC", ignoreCase = true) -> "H264"
        else -> null
    }

    fun codecLabel(codec: String): String = when {
        codec.equals("H264", true) -> "H264"
        codec.equals("HEVC", true) -> "HEVC"
        codec.equals("AV1", true) -> "AV1"
        else -> codec
    }

    fun languageFromText(text: String): String? =
        languageTokens.firstOrNull { (token, _) -> text.contains(token, ignoreCase = true) }?.second

    fun isDubText(text: String): Boolean = text.contains("Dub", ignoreCase = true)
}

/** Device codec capability (spec 5.4). Unknown codecs are allowed and simply tried. */
object CodecSupport {
    private val cache = mutableMapOf<String, Boolean>()

    fun mimeFor(codec: String?): String? = when {
        codec == null -> null
        codec.equals("AV1", ignoreCase = true) -> "video/av01"
        codec.equals("HEVC", ignoreCase = true) ||
            codec.equals("H265", ignoreCase = true) -> "video/hevc"
        codec.equals("H264", ignoreCase = true) ||
            codec.equals("AVC", ignoreCase = true) -> "video/avc"
        else -> null
    }

    fun isSupported(codec: String?): Boolean {
        val mime = mimeFor(codec) ?: return true
        return cache.getOrPut(mime) {
            android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                .codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                }
        }
    }
}

/** Parses a media_sources row, tolerating missing columns. */
fun mediaSourceFromJson(json: JSONObject): MediaSource {
    fun optStringOrNull(name: String): String? =
        if (json.has(name) && !json.isNull(name)) json.optString(name).takeIf { it.isNotBlank() }
        else null

    fun optIntOrNull(name: String): Int? =
        if (json.has(name) && !json.isNull(name)) {
            val raw = json.opt(name)
            when (raw) {
                is Number -> raw.toInt()
                else -> json.opt(name).toString().toIntOrNull()
            }
        } else null

    val headersJson = if (json.has("headers") && json.opt("headers") is JSONObject) {
        json.getJSONObject("headers")
    } else null
    val headers = buildMap {
        headersJson?.keys()?.forEach { key ->
            val value = headersJson.opt(key)?.toString()
            if (!value.isNullOrBlank()) put(key, value)
        }
    }

    return MediaSource(
        id = optStringOrNull("id"),
        contentId = optStringOrNull("content_id"),
        episodeId = optStringOrNull("episode_id") ?: optStringOrNull("episode_key"),
        seasonNumber = optIntOrNull("season_number"),
        episodeNumber = optIntOrNull("episode_number"),
        serverName = optStringOrNull("server_name") ?: "Server",
        mediaUrl = optStringOrNull("media_url") ?: "",
        quality = optIntOrNull("quality"),
        linkType = optStringOrNull("link_type"),
        status = optStringOrNull("status"),
        headers = headers,
        columnLanguage = optStringOrNull("language"),
        columnIsDub = if (json.has("is_dub") && !json.isNull("is_dub")) json.optBoolean("is_dub") else null,
        columnCodec = optStringOrNull("codec"),
        resolvedAt = optStringOrNull("resolved_at"),
        expiresAt = optStringOrNull("expires_at"),
    )
}

/** Reasons a read can fail; every one of them is shown honestly in the UI (spec section 8). */
sealed class DataException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConfigured : DataException("not_configured")
    class PermissionBlocked : DataException("permission_blocked")
    class AuthFailed(detail: String) : DataException("auth_failed:$detail")
    class Offline(cause: Throwable?) : DataException("offline", cause)
    class Http(val code: Int, detail: String) : DataException("http_$code:$detail")
    class Malformed(detail: String) : DataException("malformed:$detail")
}
