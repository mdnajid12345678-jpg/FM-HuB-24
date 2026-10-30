package com.fmhub24.android.data.repo

import com.fmhub24.android.data.local.HistoryEntity
import com.fmhub24.android.data.local.MyListEntity
import com.fmhub24.android.data.local.FavoriteChannelEntity
import com.fmhub24.android.data.local.SearchHistoryEntity
import com.fmhub24.android.data.local.WatchStateDao
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HistoryEntry(
    val key: String,
    val contentId: String,
    val title: String,
    val posterUrl: String?,
    val contentType: ContentType,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val isEpisode: Boolean get() = seasonNumber != null && episodeNumber != null
    val episodeLabel: String
        get() = if (isEpisode) "S${seasonNumber} E${episodeNumber}" else ""
}

data class MyListEntry(
    val contentId: String,
    val title: String,
    val posterUrl: String?,
    val contentType: ContentType,
    val addedAt: Long,
)

/**
 * Watch history, My list, favourite channels and search history. All of it stays on the phone
 * (spec 5.5); an optional cloud sync can be added later behind this repository.
 */
@Singleton
class WatchStateRepository @Inject constructor(
    private val dao: WatchStateDao,
) {
    val history: Flow<List<HistoryEntry>> = dao.observeHistory().map { rows -> rows.map(::toEntry) }

    val myList: Flow<List<MyListEntry>> = dao.observeMyList().map { rows ->
        rows.map {
            MyListEntry(
                contentId = it.contentId,
                title = it.title,
                posterUrl = it.posterUrl,
                contentType = ContentType.from(it.contentType),
                addedAt = it.addedAt,
            )
        }
    }

    val favoriteChannels: Flow<List<FavoriteChannelEntity>> = dao.observeFavorites()

    val searchHistory: Flow<List<String>> = dao.observeSearchHistory().map { rows -> rows.map { it.query } }

    fun isInMyList(contentId: String): Flow<Boolean> = dao.observeMyList().map { rows ->
        rows.any { it.contentId == contentId }
    }

    fun isFavorite(channelId: String): Flow<Boolean> = dao.observeFavorites().map { rows ->
        rows.any { it.contentId == channelId }
    }

    suspend fun recordProgress(
        item: MediaItem,
        episode: EpisodeItem?,
        positionMs: Long,
        durationMs: Long,
    ) {
        val key = episode?.key ?: item.id
        dao.upsertHistory(
            HistoryEntity(
                key = key,
                contentId = item.id,
                title = if (episode == null) item.title else "${item.title} · S${episode.seasonNumber} E${episode.episodeNumber}",
                posterUrl = episode?.posterUrl ?: item.posterUrl,
                contentType = item.contentType.raw,
                seasonNumber = episode?.seasonNumber,
                episodeNumber = episode?.episodeNumber,
                positionMs = positionMs,
                durationMs = durationMs,
                watchedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun historyFor(contentId: String, episodeKey: String?): HistoryEntry? =
        dao.historyByKey(episodeKey ?: contentId)?.let(::toEntry)

    suspend fun removeHistory(key: String) = dao.deleteHistory(key)

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun toggleMyList(item: MediaItem) {
        if (dao.myListItem(item.id) != null) {
            dao.deleteMyList(item.id)
        } else {
            dao.upsertMyList(
                MyListEntity(
                    contentId = item.id,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    contentType = item.contentType.raw,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun removeFromMyList(contentId: String) = dao.deleteMyList(contentId)

    suspend fun toggleFavorite(channel: MediaItem, genresJson: String?) {
        if (dao.favorite(channel.id) != null) {
            dao.deleteFavorite(channel.id)
        } else {
            dao.upsertFavorite(
                FavoriteChannelEntity(
                    contentId = channel.id,
                    title = channel.title,
                    posterUrl = channel.posterUrl,
                    genresJson = genresJson,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun recordSearch(query: String) {
        val normalized = query.trim()
        if (normalized.length < 2) return
        dao.upsertSearch(SearchHistoryEntity(query = normalized, searchedAt = System.currentTimeMillis()))
    }

    suspend fun removeSearch(query: String) = dao.deleteSearch(query)

    suspend fun clearSearchHistory() = dao.clearSearchHistory()

    private fun toEntry(entity: HistoryEntity) = HistoryEntry(
        key = entity.key,
        contentId = entity.contentId,
        title = entity.title,
        posterUrl = entity.posterUrl,
        contentType = ContentType.from(entity.contentType),
        seasonNumber = entity.seasonNumber,
        episodeNumber = entity.episodeNumber,
        positionMs = entity.positionMs,
        durationMs = entity.durationMs,
        watchedAt = entity.watchedAt,
    )
}
