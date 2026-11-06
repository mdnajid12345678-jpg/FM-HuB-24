package com.fmhub24.android.data.provider

import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.model.SeasonItem

/**
 * What the app asks of a content source. The catalog, the player and the live screens talk to this
 * interface only, so nothing in the UI knows where a title comes from.
 *
 * Implementations: [BundledContentProvider] wraps a provider from the shared ABI (either compiled
 * into the app or loaded from a `.fmkp` plugin). Repositories never touch the network themselves.
 */
interface ContentProvider {
    /** Stable id of the provider, e.g. "archive" or "iptv". */
    val id: String

    /** Display name, for logs and for a future provider picker. */
    val name: String

    /** True when the provider serves live channels (the Live tab) instead of a catalog. */
    val supportsLive: Boolean

    /** The whole catalog. Called on refresh; the result replaces the cached catalog. */
    suspend fun catalog(): List<MediaItem>

    /** Free-text search, used when a title is not in the local cache yet. */
    suspend fun search(query: String): List<MediaItem>

    /** One title, or null when the provider no longer has it. */
    suspend fun media(id: String): MediaItem?

    suspend fun seasons(contentId: String): List<SeasonItem>

    suspend fun episodes(contentId: String): List<EpisodeItem>

    /** Playable links, read fresh for every playback (they are signed or short lived). */
    suspend fun sources(contentId: String, episode: EpisodeItem?): List<MediaSource>

    suspend fun liveChannels(): List<MediaItem> = emptyList()

    suspend fun liveSources(channelId: String): List<MediaSource> = emptyList()
}
