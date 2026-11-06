package com.fmhub24.provider.iptv

import com.fmhub24.provider.api.FmHubProvider
import com.fmhub24.provider.api.ProviderEpisode
import com.fmhub24.provider.api.ProviderHttp
import com.fmhub24.provider.api.ProviderMedia
import com.fmhub24.provider.api.ProviderSeason
import com.fmhub24.provider.api.ProviderSource

/**
 * Live TV from the public iptv-org index (`iptv-org.github.io/iptv/index.m3u`): a plain M3U of
 * publicly available channels with their logo, group and stream URL.
 *
 * The playlist is a few megabytes, so it is downloaded once and kept in memory for a few hours.
 * Streams that need a specific User-Agent or Referer carry those as request headers, exactly as
 * the playlist declares them, because the player would get 403s without them.
 */
class IptvOrgProvider : FmHubProvider {
    override val id: String = "iptv"
    override val name: String = "Public IPTV (iptv-org)"
    override val supportsLive: Boolean = true

    @Volatile
    private var cache: Snapshot? = null

    override fun catalog(): List<ProviderMedia> = emptyList()

    override fun search(query: String): List<ProviderMedia> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return snapshot().channels
            .filter { it.title.contains(needle, ignoreCase = true) }
            .take(SEARCH_LIMIT)
    }

    override fun media(id: String): ProviderMedia? =
        snapshot().channels.firstOrNull { it.id == id }

    override fun seasons(contentId: String): List<ProviderSeason> = emptyList()

    override fun episodes(contentId: String): List<ProviderEpisode> = emptyList()

    override fun sources(contentId: String, episode: ProviderEpisode?): List<ProviderSource> =
        emptyList()

    override fun liveChannels(): List<ProviderMedia> = snapshot().channels

    override fun liveSources(channelId: String): List<ProviderSource> =
        snapshot().streams[channelId].orEmpty()

    // ---------------------------------------------------------------------------------------------

    private fun snapshot(): Snapshot {
        val current = cache
        if (current != null && System.currentTimeMillis() - current.loadedAtMs < CACHE_TTL_MS) {
            return current
        }
        val playlist = ProviderHttp.getText(PLAYLIST_URL, timeoutMs = DOWNLOAD_TIMEOUT_MS)
        val parsed = parsePlaylist(playlist)
        if (parsed.channels.isEmpty()) {
            current?.let { return it }
            throw IllegalStateException("iptv-org playlist contained no usable channels")
        }
        cache = parsed
        return parsed
    }

    /** Public to the module (not to plugins) so the parser is covered by real unit tests. */
    internal fun parsePlaylist(playlist: String): Snapshot {
        val channels = LinkedHashMap<String, MutableChannel>()
        var pending: PendingChannel? = null
        var pendingHeaders: MutableMap<String, String> = mutableMapOf()

        playlist.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> return@forEach
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                }
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> {
                    val option = line.substringAfter(':', "").trim()
                    val key = option.substringBefore('=', "").trim().lowercase()
                    val value = option.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) {
                        when (key) {
                            "http-user-agent" -> pendingHeaders["User-Agent"] = value
                            "http-referrer", "http-referer" -> pendingHeaders["Referer"] = value
                        }
                    }
                }
                line.startsWith("#") -> return@forEach
                else -> {
                    val channel = pending
                    pending = null
                    if (channel != null && line.startsWith("http", ignoreCase = true)) {
                        add(channels, channel, line, pendingHeaders)
                    }
                    pendingHeaders = mutableMapOf()
                }
            }
        }

        val media = buildList {
            channels.values.forEach { channel ->
                add(
                    ProviderMedia(
                        id = channel.id,
                        title = channel.name,
                        contentType = "live",
                        posterUrl = channel.logo,
                        genres = listOf(channel.group),
                        status = "live",
                    ),
                )
            }
        }
        val streams = channels.values.associate { channel -> channel.id to channel.streams }
        return Snapshot(
            loadedAtMs = System.currentTimeMillis(),
            channels = media,
            streams = streams,
        )
    }

    private fun add(
        channels: MutableMap<String, MutableChannel>,
        pending: PendingChannel,
        url: String,
        headers: Map<String, String>,
    ) {
        if (pending.name.isBlank()) return
        val id = channelId(pending)
        val existing = channels[id]
        val channel = existing ?: MutableChannel(
            id = id,
            name = pending.name,
            logo = pending.logo,
            group = pending.group,
        ).also { channels[id] = it }
        if (channel.streams.size >= MAX_STREAMS_PER_CHANNEL) return
        channel.streams += ProviderSource(
            serverName = "${channel.name} • ${qualityOf(pending.name, url)?.let { "${it}p" } ?: "Live"}",
            url = url,
            linkType = if (url.contains(".m3u8", ignoreCase = true)) "M3U8" else "VIDEO",
            quality = qualityOf(pending.name, url),
            headers = linkedMapOf<String, String>().apply {
                // The EXTINF attribute is the default; an #EXTVLCOPT line right before the URL
                // refines it for that one stream, so it is applied last and wins.
                pending.userAgent?.let { put("User-Agent", it) }
                pending.referrer?.let { put("Referer", it) }
                headers["User-Agent"]?.let { put("User-Agent", it) }
                headers["Referer"]?.let { put("Referer", it) }
            },
            language = "Original",
            isDub = false,
        )
    }

    private fun parseExtInf(line: String): PendingChannel {
        val attributes = ATTRIBUTE.findAll(line).associate { match ->
            match.groupValues[1].lowercase() to match.groupValues[2]
        }
        val name = channelNameOf(line)
        val group = attributes["group-title"]
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Other"
        val id = attributes["tvg-id"]?.trim()?.takeIf { it.isNotBlank() }
        return PendingChannel(
            rawId = id,
            name = cleanupName(name),
            logo = attributes["tvg-logo"]?.trim()?.takeIf { it.startsWith("http") },
            group = group,
            userAgent = attributes["http-user-agent"]?.trim()?.takeIf { it.isNotBlank() },
            referrer = attributes["http-referrer"]?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: attributes["referrer"]?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    /** The channel name follows the first comma outside the attribute quotes. */
    private fun channelNameOf(line: String): String {
        var quoted = false
        line.forEachIndexed { index, char ->
            when {
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> return line.substring(index + 1).trim()
            }
        }
        return ""
    }

    /** Some names carry a quality or restriction marker that is not part of the channel name. */
    private fun cleanupName(name: String): String = name
        .replace(Regex("\\s*\\(\\d{3,4}p\\)"), "")
        .replace(Regex("\\s*\\[(?:Geo-blocked|Not 24/7)]"), "")
        .trim()

    private fun qualityOf(name: String, url: String): Int? {
        val fromName = Regex("(\\d{3,4})p").find(name)?.groupValues?.get(1)?.toIntOrNull()
        if (fromName != null && fromName in MIN_QUALITY..MAX_QUALITY) return fromName
        val fromUrl = Regex("/(\\d{3,4})p?/").find(url)?.groupValues?.get(1)?.toIntOrNull()
        return fromUrl?.takeIf { it in MIN_QUALITY..MAX_QUALITY }
    }

    private fun channelId(pending: PendingChannel): String {
        val base = pending.rawId?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: pending.name.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        return "iptv:$base"
    }

    private data class PendingChannel(
        val rawId: String?,
        val name: String,
        val logo: String?,
        val group: String,
        val userAgent: String?,
        val referrer: String?,
    )

    private class MutableChannel(
        val id: String,
        val name: String,
        val logo: String?,
        val group: String,
    ) {
        val streams = mutableListOf<ProviderSource>()
    }

    internal data class Snapshot(
        val loadedAtMs: Long,
        val channels: List<ProviderMedia>,
        val streams: Map<String, List<ProviderSource>>,
    )

    private companion object {
        const val PLAYLIST_URL = "https://iptv-org.github.io/iptv/index.m3u"
        const val DOWNLOAD_TIMEOUT_MS = 60_000
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        const val MAX_STREAMS_PER_CHANNEL = 4
        const val SEARCH_LIMIT = 60
        const val MIN_QUALITY = 144
        const val MAX_QUALITY = 4320
        val ATTRIBUTE = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    }
}
