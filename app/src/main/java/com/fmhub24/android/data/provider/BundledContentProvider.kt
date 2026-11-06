package com.fmhub24.android.data.provider

import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.model.SeasonItem
import com.fmhub24.provider.api.FmHubProvider
import com.fmhub24.provider.api.ProviderEpisode
import com.fmhub24.provider.api.ProviderMedia
import com.fmhub24.provider.api.ProviderSeason
import com.fmhub24.provider.api.ProviderSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges the provider ABI to the app: runs the blocking provider calls on the IO dispatcher and
 * maps provider models to the app's catalog models.
 *
 * Every failure becomes a [DataException] so the UI can show what actually went wrong: a provider
 * that cannot reach its source reports an offline error instead of an empty catalog.
 */
class BundledContentProvider(
    private val delegate: FmHubProvider,
) : ContentProvider {
    override val id: String get() = delegate.id
    override val name: String get() = delegate.name
    override val supportsLive: Boolean get() = delegate.supportsLive

    override suspend fun catalog(): List<MediaItem> = call {
        delegate.catalog().map(::toMediaItem).distinctBy { it.id }
    }

    override suspend fun search(query: String): List<MediaItem> = call {
        delegate.search(query).map(::toMediaItem).distinctBy { it.id }
    }

    override suspend fun media(id: String): MediaItem? = call {
        delegate.media(id)?.let(::toMediaItem)
    }

    override suspend fun seasons(contentId: String): List<SeasonItem> = call {
        delegate.seasons(contentId).map { season -> toSeasonItem(contentId, season) }
    }

    override suspend fun episodes(contentId: String): List<EpisodeItem> = call {
        delegate.episodes(contentId).map { episode -> toEpisodeItem(contentId, episode) }
    }

    override suspend fun sources(contentId: String, episode: EpisodeItem?): List<MediaSource> = call {
        val providerEpisode = episode?.let { item ->
            ProviderEpisode(
                id = item.id,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
                title = item.title,
                description = item.description,
                posterUrl = item.posterUrl,
                sortOrder = item.sortOrder,
            )
        }
        delegate.sources(contentId, providerEpisode)
            .filter { it.url.isNotBlank() }
            .map { source -> toMediaSource(contentId, episode, source) }
    }

    /**
     * Channels are capped per category: a public IPTV index can hold thousands of channels in one
     * group, and the Live screen renders each group as one card that would otherwise be tens of
     * thousands of pixels tall.
     */
    override suspend fun liveChannels(): List<MediaItem> = call {
        delegate.liveChannels()
            .map(::toMediaItem)
            .distinctBy { it.id }
            .groupBy { it.genres.firstOrNull() ?: "Other" }
            .values
            .flatMap { group ->
                group.sortedBy { it.title.lowercase() }.take(MAX_CHANNELS_PER_GROUP)
            }
    }

    override suspend fun liveSources(channelId: String): List<MediaSource> = call {
        delegate.liveSources(channelId)
            .filter { it.url.isNotBlank() }
            .map { source -> toMediaSource(channelId, null, source) }
    }

    private suspend fun <T> call(block: () -> T): T = try {
        withContext(Dispatchers.IO) { block() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: DataException) {
        throw error
    } catch (error: IOException) {
        throw DataException.Offline(error)
    } catch (error: Exception) {
        throw DataException.Malformed("$id: ${error.message ?: error.javaClass.simpleName}")
    }

    private fun toMediaItem(media: ProviderMedia): MediaItem = MediaItem(
        id = media.id,
        canonicalKey = media.canonicalKey,
        title = media.title,
        originalTitle = media.originalTitle,
        contentType = ContentType.from(media.contentType),
        releaseYear = media.releaseYear,
        posterUrl = media.posterUrl,
        description = media.description,
        genres = media.genres,
        status = media.status,
        rating = media.rating,
        createdAt = media.createdAt,
        updatedAt = media.updatedAt,
    )

    private fun toSeasonItem(contentId: String, season: ProviderSeason): SeasonItem = SeasonItem(
        contentId = contentId,
        seasonNumber = season.seasonNumber,
        name = season.name,
        sortOrder = season.sortOrder,
    )

    private fun toEpisodeItem(contentId: String, episode: ProviderEpisode): EpisodeItem = EpisodeItem(
        id = episode.id,
        contentId = contentId,
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
        title = episode.title,
        description = episode.description,
        posterUrl = episode.posterUrl,
        sortOrder = episode.sortOrder,
    )

    private fun toMediaSource(
        contentId: String,
        episode: EpisodeItem?,
        source: ProviderSource,
    ): MediaSource = MediaSource(
        id = null,
        contentId = contentId,
        episodeId = episode?.let { it.id ?: it.key },
        seasonNumber = episode?.seasonNumber,
        episodeNumber = episode?.episodeNumber,
        serverName = source.serverName,
        mediaUrl = source.url,
        quality = source.quality,
        linkType = source.linkType,
        // Provider rows are playable right now; MediaSource.isPlayable requires this status.
        status = "resolved",
        headers = source.headers,
        columnLanguage = source.language,
        columnIsDub = source.isDub,
        columnCodec = source.codec,
        resolvedAt = null,
        expiresAt = null,
    )

    private companion object {
        const val MAX_CHANNELS_PER_GROUP = 80
    }
}
