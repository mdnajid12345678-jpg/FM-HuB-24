package com.fmhub24.android.data.repo

import com.fmhub24.android.data.local.CatalogDao
import com.fmhub24.android.data.local.EpisodeEntity
import com.fmhub24.android.data.local.MediaContentEntity
import com.fmhub24.android.data.local.SeasonEntity
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.SeasonItem
import com.fmhub24.android.data.provider.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/**
 * Catalog reads and the incremental Room cache. Content comes from the active [ProviderRegistry]
 * provider; the cache keeps the catalog usable offline and keeps search, history and downloads
 * working without a network round trip. Playable links are never cached here.
 *
 * Structure (seasons and episodes) is fetched on demand when a title is opened, because a catalog
 * of hundreds of titles would otherwise mean hundreds of requests on every refresh.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val providers: ProviderRegistry,
) {
    private val refreshMutex = Mutex()

    @Volatile
    private var lastSuccessfulRefreshMs = 0L

    val contents: Flow<List<MediaItem>> = dao.observeContents().map { rows -> rows.map(::toMediaItem) }

    /**
     * Escapes the LIKE wildcards the user may type, so "100%" searches for the literal text "100%"
     * instead of matching everything. Paired with ESCAPE '\' in the DAO query.
     */
    fun escapeForLike(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    fun search(rawQuery: String): Flow<List<MediaItem>> =
        dao.observeSearch(escapeForLike(rawQuery)).map { rows -> rows.map(::toMediaItem) }

    suspend fun cachedContent(id: String): MediaItem? = dao.content(id)?.let(::toMediaItem)

    suspend fun cachedSeasons(contentId: String): List<SeasonItem> =
        dao.seasons(contentId).map { it.toSeason() }

    /**
     * One row per episode. The row key also carries the provider episode id, so two rows with the
     * same season/episode number would otherwise map to the same [EpisodeItem.key] and make every
     * LazyColumn keyed on it crash.
     */
    suspend fun cachedEpisodes(contentId: String): List<EpisodeItem> =
        dao.episodes(contentId).map(::toEpisode).distinctBy { it.key }

    fun observeSeasons(contentId: String): Flow<List<SeasonItem>> =
        dao.observeSeasons(contentId).map { rows -> rows.map { it.toSeason() } }

    fun observeEpisodes(contentId: String): Flow<List<EpisodeItem>> =
        dao.observeEpisodes(contentId).map { rows -> rows.map(::toEpisode).distinctBy { it.key } }

    /**
     * Pulls the provider catalog and replaces the cached one. Returns null on success or the
     * failure to show in the UI. Titles that the provider no longer lists are dropped together
     * with their cached structure.
     */
    suspend fun refresh(): DataException? = refreshMutex.withLock {
        if (System.currentTimeMillis() - lastSuccessfulRefreshMs < REFRESH_DEBOUNCE_MS) {
            return@withLock null
        }
        try {
            val provider = providers.catalogProvider() ?: throw DataException.NotConfigured()
            val items = provider.catalog()
            if (items.isEmpty()) throw DataException.Malformed("${provider.id}: empty catalog")
            val entities = items.map(::toContentEntity)
            dao.upsertContents(entities)
            val currentIds = entities.mapTo(mutableSetOf()) { it.id }
            dao.contents()
                .filter { it.id !in currentIds }
                .forEach { stale ->
                    dao.clearSeasons(stale.id)
                    dao.clearEpisodes(stale.id)
                    dao.deleteContent(stale.id)
                }
            lastSuccessfulRefreshMs = System.currentTimeMillis()
            null
        } catch (error: DataException) {
            error
        } catch (error: Exception) {
            DataException.Malformed(error.message ?: "sync")
        }
    }

    /**
     * Asks every catalog provider for titles that are not in the cache yet. Nothing is written to
     * the catalog table here: the cache mirrors what the provider lists, and a search hit is cached
     * when the user opens it ([ensureContent]).
     *
     * One provider failing does not hide the results of the others; the error is only reported when
     * nothing at all came back.
     */
    suspend fun searchProvider(rawQuery: String): List<MediaItem> {
        val query = rawQuery.trim()
        if (query.length < MIN_REMOTE_QUERY) return emptyList()
        val available = providers.catalogProviders()
        if (available.isEmpty()) throw DataException.NotConfigured()
        val merged = LinkedHashMap<String, MediaItem>()
        var failure: DataException? = null
        available.forEach { provider ->
            try {
                provider.search(query).forEach { item -> merged.putIfAbsent(item.id, item) }
            } catch (error: DataException) {
                if (failure == null) failure = error
            }
        }
        if (merged.isEmpty()) failure?.let { throw it }
        return merged.values.toList()
    }

    /** Pulls one title when it is missing from the cache (deep link, cold start). */
    suspend fun ensureContent(id: String): DataException? {
        if (dao.content(id) != null) return null
        return try {
            val provider = providers.catalogProvider() ?: throw DataException.NotConfigured()
            val item = provider.media(id) ?: throw DataException.Malformed("${provider.id}: no $id")
            dao.upsertContents(listOf(toContentEntity(item)))
            null
        } catch (error: DataException) {
            error
        } catch (error: Exception) {
            DataException.Malformed(error.message ?: "content")
        }
    }

    /** Seasons and episodes for one title, stored in the cache. */
    suspend fun ensureStructure(contentId: String): DataException? {
        val hasCachedStructure = dao.seasons(contentId).isNotEmpty() ||
            dao.episodes(contentId).isNotEmpty()
        val error = syncStructure(listOf(contentId))
        return if (error != null && hasCachedStructure) null else error
    }

    suspend fun syncStructure(contentIds: List<String>): DataException? {
        if (contentIds.isEmpty()) return null
        contentIds.distinct().chunked(STRUCTURE_BATCH).forEach { batch ->
            val error = syncStructureBatch(batch)
            if (error != null) return error
        }
        return null
    }

    private suspend fun syncStructureBatch(contentIds: List<String>): DataException? {
        return try {
            val provider = providers.catalogProvider() ?: throw DataException.NotConfigured()
            val seasons = mutableListOf<SeasonEntity>()
            val episodes = mutableListOf<EpisodeEntity>()
            contentIds.forEach { contentId ->
                provider.seasons(contentId).forEach { season ->
                    seasons += SeasonEntity(
                        contentId = contentId,
                        seasonNumber = season.seasonNumber,
                        name = season.name,
                        sortOrder = season.sortOrder,
                    )
                }
                provider.episodes(contentId).forEach { episode ->
                    episodes += EpisodeEntity(
                        key = "${contentId}|${episode.seasonNumber}|${episode.episodeNumber}|${episode.id.orEmpty()}",
                        contentId = contentId,
                        episodeId = episode.id,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        title = episode.title,
                        description = episode.description,
                        posterUrl = episode.posterUrl,
                        sortOrder = episode.sortOrder,
                    )
                }
            }
            contentIds.forEach { contentId ->
                if (seasons.none { it.contentId == contentId }) dao.clearSeasons(contentId)
                if (episodes.none { it.contentId == contentId }) dao.clearEpisodes(contentId)
            }
            if (seasons.isNotEmpty()) dao.upsertSeasons(seasons)
            if (episodes.isNotEmpty()) dao.upsertEpisodes(episodes)
            null
        } catch (error: DataException) {
            error
        } catch (error: Exception) {
            DataException.Malformed(error.message ?: "structure")
        }
    }

    /** Re-reads the structure of one title, used when the user asks for fresh data. */
    suspend fun reloadStructure(contentId: String): DataException? = try {
        syncStructure(listOf(contentId))
    } catch (error: DataException) {
        error
    }

    // ------------------------------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------------------------------

    companion object {
        const val STRUCTURE_BATCH = 20
        const val REFRESH_DEBOUNCE_MS = 30_000L

        /** One character would match half of archive.org; provider search needs a real word. */
        const val MIN_REMOTE_QUERY = 2

        fun genresJson(genres: List<String>): String? {
            if (genres.isEmpty()) return null
            val array = JSONArray()
            genres.forEach { genre -> array.put(genre) }
            return array.toString()
        }

        fun parseGenres(genresJson: String?): List<String> {
            if (genresJson.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(genresJson)
                buildList {
                    for (i in 0 until array.length()) {
                        array.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun toContentEntity(item: MediaItem): MediaContentEntity = MediaContentEntity(
            id = item.id,
            canonicalKey = item.canonicalKey,
            title = item.title,
            originalTitle = item.originalTitle,
            contentType = item.contentType.raw,
            releaseYear = item.releaseYear,
            posterUrl = item.posterUrl,
            description = item.description,
            genresJson = genresJson(item.genres),
            status = item.status,
            rating = item.rating,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
        )

        fun toMediaItem(entity: MediaContentEntity): MediaItem = MediaItem(
            id = entity.id,
            canonicalKey = entity.canonicalKey,
            title = entity.title,
            originalTitle = entity.originalTitle,
            contentType = ContentType.from(entity.contentType),
            releaseYear = entity.releaseYear,
            posterUrl = entity.posterUrl,
            description = entity.description,
            genres = parseGenres(entity.genresJson),
            status = entity.status,
            rating = entity.rating,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

        private fun SeasonEntity.toSeason() = SeasonItem(
            contentId = contentId,
            seasonNumber = seasonNumber,
            name = name,
            sortOrder = sortOrder,
        )

        private fun toEpisode(entity: EpisodeEntity) = EpisodeItem(
            id = entity.episodeId,
            contentId = entity.contentId,
            seasonNumber = entity.seasonNumber,
            episodeNumber = entity.episodeNumber,
            title = entity.title,
            description = entity.description,
            posterUrl = entity.posterUrl,
            sortOrder = entity.sortOrder,
        )
    }
}
