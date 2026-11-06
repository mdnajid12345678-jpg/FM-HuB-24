package com.fmhub24.android.data.repo

import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.provider.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads playable links from the active provider for every playback or download. Rows are never
 * cached: signed or short-lived URLs must be read fresh (spec 5.2 rule 3).
 */
@Singleton
class SourcesRepository @Inject constructor(
    private val providers: ProviderRegistry,
) {
    /** All sources attached to a title, including its episodes' rows. */
    suspend fun forContent(contentId: String): List<MediaSource> = sourcesFor(contentId, null)

    /** Sources for a movie (episode == null) or for one episode. */
    suspend fun forTarget(contentId: String, episode: EpisodeItem?): List<MediaSource> =
        sourcesFor(contentId, episode)

    /**
     * A transient language index for the Explore filter. Providers do not publish a catalog-wide
     * language list, so the languages are derived from the sources of the first few titles; the
     * index stays in memory and is never stored (spec 5.5).
     */
    suspend fun languageIndex(contentIds: List<String>): Map<String, Set<String>> {
        if (contentIds.isEmpty()) return emptyMap()
        val provider = providers.catalogProvider() ?: return emptyMap()
        val index = mutableMapOf<String, MutableSet<String>>()
        contentIds.distinct().take(LANGUAGE_INDEX_LIMIT).forEach { contentId ->
            val sources = runCatching { provider.sources(contentId, null) }
                .getOrNull()
                .orEmpty()
            sources.forEach { source ->
                val language = source.language?.takeIf { it.isNotBlank() }
                    ?: SourceLabel.languageOf(source.serverName)
                    ?: "Original"
                index.getOrPut(contentId) { mutableSetOf() }.add(language)
            }
        }
        return index
    }

    private suspend fun sourcesFor(contentId: String, episode: EpisodeItem?): List<MediaSource> {
        val provider = providers.catalogProvider() ?: throw DataException.NotConfigured()
        return provider.sources(contentId, episode).filter { it.isPlayable }
    }

    companion object {
        const val LANGUAGE_INDEX_LIMIT = 12
    }
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

        // Lowest codec rank wins (H264 first, then HEVC), then the highest quality, then HLS.
        return pool.minWithOrNull(
            compareBy<MediaSource> { codecRank(it.codec) }
                .thenByDescending { it.quality ?: 0 }
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
