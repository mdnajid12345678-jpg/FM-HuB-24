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
import com.fmhub24.android.data.remote.SupabaseRest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catalog reads and the incremental Room cache (spec 5.1 and 5.5). media_sources is never
 * cached here. Network errors are returned, never hidden.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val dao: CatalogDao,
    private val rest: SupabaseRest,
) {
    val contents: Flow<List<MediaItem>> = dao.observeContents().map { rows -> rows.map(::toMediaItem) }

    fun search(query: String): Flow<List<MediaItem>> = dao.observeSearch(query)
        .map { rows -> rows.map(::toMediaItem) }

    suspend fun cachedContent(id: String): MediaItem? = dao.content(id)?.let(::toMediaItem)

    suspend fun cachedSeasons(contentId: String): List<SeasonItem> =
        dao.seasons(contentId).map { it.toSeason() }

    suspend fun cachedEpisodes(contentId: String): List<EpisodeItem> =
        dao.episodes(contentId).map(::toEpisode)

    fun observeSeasons(contentId: String): Flow<List<SeasonItem>> =
        dao.observeSeasons(contentId).map { rows -> rows.map { it.toSeason() } }

    fun observeEpisodes(contentId: String): Flow<List<EpisodeItem>> =
        dao.observeEpisodes(contentId).map { rows -> rows.map(::toEpisode) }

    /**
     * Incremental sync: only rows newer than the newest cached row are pulled, then their seasons
     * and episodes. Returns null on success or the failure to show in the UI.
     */
    suspend fun refresh(): DataException? {
        return try {
            val latest = dao.latestUpdatedAt()
            val updatedIds = mutableSetOf<String>()
            var offset = 0
            while (offset < MAX_ROWS) {
                val page = rest.select(
                    table = "media_contents",
                    query = buildList {
                        add("select" to "*")
                        if (latest != null) add("updated_at" to "gt.$latest")
                        add("order" to "updated_at.asc")
                        add("limit" to PAGE_SIZE.toString())
                        add("offset" to offset.toString())
                    },
                )
                val rows = page.mapNotNull(::contentFromJson)
                if (rows.isEmpty()) break
                dao.upsertContents(rows)
                rows.forEach { updatedIds += it.id }
                if (rows.size < PAGE_SIZE) break
                offset += PAGE_SIZE
            }
            if (updatedIds.isNotEmpty()) {
                syncStructure(updatedIds.take(STRUCTURE_BATCH))
            }
            null
        } catch (error: DataException) {
            error
        } catch (error: Exception) {
            DataException.Malformed(error.message ?: "sync")
        }
    }

    /** Pulls one title when it is missing from the cache (deep link, cold start). */
    suspend fun ensureContent(id: String): DataException? {
        if (dao.content(id) != null) return null
        return try {
            val rows = rest.select(
                table = "media_contents",
                query = listOf("select" to "*", "id" to "eq.$id"),
            )
            rows.mapNotNull(::contentFromJson).forEach { dao.upsertContents(listOf(it)) }
            null
        } catch (error: DataException) {
            error
        } catch (error: Exception) {
            DataException.Malformed(error.message ?: "content")
        }
    }

    /** Seasons and episodes for one title, stored in the cache (spec 5.1 ordering). */
    suspend fun ensureStructure(contentId: String): DataException? {
        if (dao.seasons(contentId).isNotEmpty() || dao.episodes(contentId).isNotEmpty()) return null
        return syncStructure(listOf(contentId))
    }

    suspend fun syncStructure(contentIds: List<String>): DataException? {
        if (contentIds.isEmpty()) return null
        return try {
            val filter = contentIds.joinToString(",") { "\"$it\"" }
            val seasons = rest.select(
                table = "seasons",
                query = listOf("select" to "*", "content_id" to "in.($filter)", "order" to "season_number.asc"),
            ).mapNotNull(::seasonFromJson)
            val episodes = rest.select(
                table = "episodes",
                query = listOf(
                    "select" to "*",
                    "content_id" to "in.($filter)",
                    "order" to "season_number.asc,episode_number.asc,sort_order.asc",
                ),
            ).mapNotNull(::episodeFromJson)

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
    suspend fun reloadStructure(contentId: String): DataException? {
        return try {
            syncStructure(listOf(contentId))
        } catch (error: DataException) {
            error
        }
    }

    // ------------------------------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------------------------------

    companion object {
        const val PAGE_SIZE = 200
        const val MAX_ROWS = 5_000
        const val STRUCTURE_BATCH = 60

        fun rowId(json: JSONObject, key: String = "id"): String? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.opt(key).toString().takeIf { it.isNotBlank() && it != "null" }
        }

        fun optText(json: JSONObject, key: String): String? {
            if (!json.has(key) || json.isNull(key)) return null
            return json.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        }

        fun optInt(json: JSONObject, key: String): Int? {
            val raw = optText(json, key) ?: return null
            return raw.toDoubleOrNull()?.toInt() ?: raw.toIntOrNull()
        }

        fun optDouble(json: JSONObject, key: String): Double? {
            val raw = optText(json, key) ?: return null
            return raw.toDoubleOrNull()
        }

        fun genresJson(json: JSONObject): String? {
            if (!json.has("genres") || json.isNull("genres")) return null
            val value = json.opt("genres")
            val array = when (value) {
                is JSONArray -> value
                is String -> runCatching { JSONArray(value) }.getOrNull()
                else -> null
            } ?: return null
            val cleaned = JSONArray()
            for (i in 0 until array.length()) {
                val genre = array.opt(i)?.toString()?.takeIf { it.isNotBlank() }
                if (genre != null) cleaned.put(genre)
            }
            return cleaned.toString()
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

        fun contentFromJson(json: JSONObject): MediaContentEntity? {
            val id = rowId(json) ?: return null
            val title = optText(json, "title") ?: optText(json, "original_title") ?: return null
            return MediaContentEntity(
                id = id,
                canonicalKey = optText(json, "canonical_key"),
                title = title,
                originalTitle = optText(json, "original_title"),
                contentType = optText(json, "content_type") ?: ContentType.OTHER.raw,
                releaseYear = optInt(json, "release_year"),
                posterUrl = optText(json, "poster_url"),
                description = optText(json, "description"),
                genresJson = genresJson(json),
                status = optText(json, "status"),
                rating = optDouble(json, "rating"),
                createdAt = optText(json, "created_at"),
                updatedAt = optText(json, "updated_at"),
            )
        }

        private fun seasonFromJson(json: JSONObject): SeasonEntity? {
            val contentId = optText(json, "content_id") ?: return null
            val seasonNumber = optInt(json, "season_number") ?: 0
            return SeasonEntity(
                contentId = contentId,
                seasonNumber = seasonNumber,
                name = optText(json, "name"),
                sortOrder = optInt(json, "sort_order"),
            )
        }

        private fun episodeFromJson(json: JSONObject): EpisodeEntity? {
            val contentId = optText(json, "content_id") ?: return null
            val seasonNumber = optInt(json, "season_number") ?: 0
            val episodeNumber = optInt(json, "episode_number") ?: 0
            return EpisodeEntity(
                key = "$contentId|$seasonNumber|$episodeNumber",
                contentId = contentId,
                episodeId = rowId(json),
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                title = optText(json, "title"),
                description = optText(json, "description"),
                posterUrl = optText(json, "poster_url"),
                sortOrder = optInt(json, "sort_order"),
            )
        }

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
