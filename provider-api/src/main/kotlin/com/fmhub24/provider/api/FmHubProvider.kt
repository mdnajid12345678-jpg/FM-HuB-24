package com.fmhub24.provider.api

/**
 * The provider contract, modelled on the way Cloudstream providers plug into their host: the app
 * owns the interface and the models, a provider only answers questions about content.
 *
 * Everything here is deliberately free of Android and coroutine types so the same interface can be
 * compiled into the app (built-in providers) and into a `.fmkp` plugin (a dex that the app loads at
 * runtime). All calls block: the host runs them on its IO dispatcher.
 *
 * A provider may return an empty list for features it does not support; it must never fabricate
 * content that does not exist on its source.
 */
interface FmHubProvider {
    /** Stable id, e.g. "archive". Used for caches and for the plugin manifest. */
    val id: String

    /** Human readable name. */
    val name: String

    /** True when this provider serves live channels instead of (or as well as) a catalog. */
    val supportsLive: Boolean get() = false

    /** The whole searchable catalog. Called on pull-to-refresh. */
    fun catalog(): List<ProviderMedia>

    /** Free text search. May be empty when the source has no search endpoint. */
    fun search(query: String): List<ProviderMedia>

    /** One title; null when the source no longer has it. */
    fun media(id: String): ProviderMedia?

    /** Seasons of a title; empty for a movie. */
    fun seasons(contentId: String): List<ProviderSeason>

    /** Episodes of a title, ordered by season and episode number; empty for a movie. */
    fun episodes(contentId: String): List<ProviderEpisode>

    /**
     * Playable links, read fresh for every playback: signed or short-lived URLs must never be
     * cached by the provider. [episode] is null for a movie.
     */
    fun sources(contentId: String, episode: ProviderEpisode?): List<ProviderSource>

    /** Live channels. Only called when [supportsLive] is true. */
    fun liveChannels(): List<ProviderMedia> = emptyList()

    /** Playable links for one live channel. */
    fun liveSources(channelId: String): List<ProviderSource> = emptyList()
}

/**
 * A title on a provider.
 *
 * [contentType] uses the app's raw content types ("movie", "series", ...). [live] mirrors
 * [ContentType.LIVE]; it is kept separate so a provider does not have to know the whole enum.
 */
data class ProviderMedia(
    val id: String,
    val title: String,
    val contentType: String,
    val originalTitle: String? = null,
    val releaseYear: Int? = null,
    val posterUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val rating: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val canonicalKey: String? = null,
)

data class ProviderSeason(
    val seasonNumber: Int,
    val name: String? = null,
    val sortOrder: Int? = null,
)

data class ProviderEpisode(
    val id: String? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String? = null,
    val description: String? = null,
    val posterUrl: String? = null,
    val sortOrder: Int? = null,
)

/**
 * One playable row. [linkType] is "M3U8" for HLS and "VIDEO" for a direct video file, matching the
 * app's media_sources link_type values. [headers] are sent with the media request (some sources
 * need a Referer or User-Agent).
 */
data class ProviderSource(
    val serverName: String,
    val url: String,
    val linkType: String,
    val quality: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val isDub: Boolean? = null,
    val codec: String? = null,
)
