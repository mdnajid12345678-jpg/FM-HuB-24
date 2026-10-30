package com.fmhub24.android.data.repo

import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.model.mediaSourceFromJson
import com.fmhub24.android.data.remote.SupabaseRest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads media_sources fresh for every playback or download (spec 5.2 rule 3): rows are never
 * cached because media_url is a short-lived signed link.
 */
@Singleton
class SourcesRepository @Inject constructor(
    private val rest: SupabaseRest,
) {
    /** All resolved sources attached to a title, including its episodes' rows. */
    suspend fun forContent(contentId: String): List<MediaSource> =
        query(listOf("select" to "*", "content_id" to "eq.$contentId", "status" to "eq.resolved"))

    /**
     * Sources for a movie (episode == null) or one episode. Reads a fresh row every time and
     * falls back to an episode-scoped query when the episode rows are not linked to the title.
     */
    suspend fun forTarget(contentId: String, episode: EpisodeItem?): List<MediaSource> {
        val byContent = forContent(contentId)
        if (episode == null) return byContent.filter { it.episodeId.isNullOrBlank() }.ifEmpty { byContent }

        val matched = byContent.filter { episode.matchesSource(it) }
        if (matched.isNotEmpty()) return matched

        val episodeRowId = episode.id
        if (!episodeRowId.isNullOrBlank()) {
            val byEpisode = query(
                listOf("select" to "*", "episode_id" to "eq.$episodeRowId", "status" to "eq.resolved"),
            )
            if (byEpisode.isNotEmpty()) return byEpisode
        }
        return emptyList()
    }

    /**
     * For the Language filter: a transient language index, never stored (spec 5.5).
     * Falls back when the owner has not added the language/is_dub columns yet.
     */
    suspend fun languageIndex(contentIds: List<String>): Map<String, Set<String>> {
        if (contentIds.isEmpty()) return emptyMap()
        val ids = contentIds.joinToString(",") { "\"$it\"" }
        val rows = try {
            query(
                listOf(
                    "select" to "content_id,server_name,language,is_dub,status",
                    "content_id" to "in.($ids)",
                ),
            )
        } catch (error: DataException.Http) {
            if (error.code != 400) throw error
            query(listOf("select" to "content_id,server_name,status", "content_id" to "in.($ids)"))
        }

        val index = mutableMapOf<String, MutableSet<String>>()
        rows.forEach { source ->
            val contentId = source.contentId ?: return@forEach
            val language = source.language?.takeIf { it.isNotBlank() }
                ?: SourceLabel.languageOf(source.serverName)
                ?: "Original"
            index.getOrPut(contentId) { mutableSetOf() }.add(language)
        }
        return index
    }

    private suspend fun query(params: List<Pair<String, String>>): List<MediaSource> =
        rest.select(table = "media_sources", query = params)
            .mapNotNull { json -> runCatching { mediaSourceFromJson(json) }.getOrNull() }
            .filter { it.isPlayable }
}

/** Language/server/quality decisions from spec 5.4. */
object SourceSelection {
    val LANGUAGE_ORDER = listOf(
        "Hindi", "Original", "Telugu", "Tamil", "Bengali", "English", "Multi",
    )

    fun languageLabel(language: String?, isDub: Boolean): String = when {
        language.isNullOrBlank() -> "Original"
        language.equals("Multi", ignoreCase = true) -> "Multi-Lang"
        isDub && language.equals("Hindi", ignoreCase = true) -> "Hindi Dub"
        isDub -> "$language Dub"
        else -> language
    }

    /** Distinct languages in mockup order, unknown ones after the known ones. */
    fun languages(sources: List<MediaSource>): List<String> {
        val present = sources.map { keyOf(it) }.distinct()
        return present.sortedBy { language ->
            val index = LANGUAGE_ORDER.indexOfFirst { it.equals(language, ignoreCase = true) }
            if (index >= 0) index else LANGUAGE_ORDER.size
        }
    }

    fun keyOf(source: MediaSource): String =
        source.language?.takeIf { it.isNotBlank() } ?: "Original"

    fun matches(source: MediaSource, languageKey: String): Boolean {
        if (languageKey.equals("Original", ignoreCase = true)) {
            return keyOf(source).equals("Original", ignoreCase = true)
        }
        return keyOf(source).equals(languageKey, ignoreCase = true)
    }

    fun forLanguage(sources: List<MediaSource>, languageKey: String): List<MediaSource> =
        sources.filter { matches(it, languageKey) }.ifEmpty { sources }

    /**
     * Auto: best supported source for the chosen language – prefer H264, then HEVC when the phone
     * supports it, highest quality up to 1080p, unknown codec allowed (spec 5.4).
     */
    fun auto(sources: List<MediaSource>, languageKey: String?, maxHeight: Int = 1080): MediaSource? {
        val pool = sources.filter { CodecSupport.isSupported(it.codec) }
            .filter { source -> source.quality == null || source.quality <= maxHeight }
            .filter { source -> languageKey == null || matches(source, languageKey) }
            .ifEmpty { sources.filter { languageKey == null || matches(it, languageKey) } }
            .ifEmpty { sources }

        return pool.maxWithOrNull(
            compareBy<MediaSource> { codecRank(it.codec) }
                .thenBy { it.quality ?: 0 }
                .thenByDescending { it.isHls },
        )
    }

    /** Lower is better: H264 first for low-end phones, then HEVC, then unknown, then AV1. */
    private fun codecRank(codec: String?): Int = when {
        codec == null -> 2
        codec.equals("H264", ignoreCase = true) -> 0
        codec.equals("HEVC", ignoreCase = true) -> 1
        codec.equals("AV1", ignoreCase = true) -> 3
        else -> 2
    }

    /** Rows of the Language and server sheet: Auto first, then every server with quality/codec. */
    fun serverRows(sources: List<MediaSource>, languageKey: String?): List<MediaSource> =
        forLanguage(sources, languageKey ?: "Original")
            .distinctBy { "${it.serverLabel}|${it.quality}|${it.codec}" }
            .sortedWith(
                compareByDescending<MediaSource> { CodecSupport.isSupported(it.codec) }
                    .thenByDescending { it.quality ?: 0 },
            )

    fun qualityLabel(quality: Int?): String = when (quality) {
        null -> "Auto"
        else -> "${quality}p"
    }

    /** Best row for the "Best" badge: supported codec, then highest quality. */
    fun recommended(rows: List<MediaSource>): MediaSource? =
        rows.filter { CodecSupport.isSupported(it.codec) }
            .maxByOrNull { it.quality ?: 0 }
            ?: rows.maxByOrNull { it.quality ?: 0 }
}

/** Language parsing that does not depend on a model instance (used for indexes and sheets). */
object SourceLabel {
    fun languageOf(serverName: String): String? {
        val tokens = listOf(
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
        return tokens.firstOrNull { (token, _) -> serverName.contains(token, ignoreCase = true) }?.second
    }
}
