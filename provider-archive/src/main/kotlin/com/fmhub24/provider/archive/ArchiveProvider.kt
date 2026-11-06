package com.fmhub24.provider.archive

import com.fmhub24.provider.api.FmHubProvider
import com.fmhub24.provider.api.ProviderEpisode
import com.fmhub24.provider.api.ProviderHttp
import com.fmhub24.provider.api.ProviderMedia
import com.fmhub24.provider.api.ProviderSeason
import com.fmhub24.provider.api.ProviderSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Internet Archive: public-domain and openly licensed films, streamed straight from archive.org.
 *
 * Every value returned here comes from the archive.org APIs:
 *  - `advancedsearch.php` for the catalog and for search,
 *  - `metadata/{identifier}` for a title and its playable files,
 *  - `download/{identifier}/{file}` for playback (archive.org answers with a 302 to a node),
 *  - `services/img/{identifier}` for the poster.
 *
 * Items that archive.org marks as dark (no files) are dropped instead of being offered as titles
 * that cannot play.
 */
class ArchiveProvider : FmHubProvider {
    override val id: String = "archive"
    override val name: String = "Internet Archive"
    override val supportsLive: Boolean = false

    /** Small LRU of item metadata so opening details and then playing does not fetch it twice. */
    private val metadataCache = LinkedHashMap<String, JSONObject>()

    override fun catalog(): List<ProviderMedia> {
        val collected = LinkedHashMap<String, ProviderMedia>()
        var failure: Exception? = null
        val requested = MOVIE_COLLECTIONS.map { it to "movie" } + TV_COLLECTIONS.map { it to "series" }
        requested.forEach { (collection, contentType) ->
            try {
                listing(collection, contentType).forEach { media ->
                    collected.putIfAbsent(media.id, media)
                }
            } catch (error: Exception) {
                failure = error
            }
        }
        if (collected.isEmpty()) failure?.let { throw it }
        return collected.values.toList()
    }

    override fun search(query: String): List<ProviderMedia> {
        val needle = query.replace(QUERY_SPECIALS, " ").trim()
        if (needle.isEmpty()) return emptyList()
        val q = buildString {
            append("(title:(\"").append(needle).append("\") OR subject:(\"").append(needle).append("\"))")
            append(" AND mediatype:movies AND collection:(")
            append((MOVIE_COLLECTIONS + TV_COLLECTIONS).joinToString(" OR "))
            append(')')
        }
        val docs = queryDocs(
            params = listOf("q" to q),
            rows = SEARCH_ROWS,
            sort = "downloads desc",
        )
        return docs.mapNotNull { doc -> docToMedia(doc, contentTypeOf(doc)) }
    }

    override fun media(id: String): ProviderMedia? {
        val root = metadata(id) ?: return null
        val meta = root.optJSONObject("metadata") ?: return null
        val files = root.optJSONArray("files") ?: return null
        // A dark item has metadata but no playable files: it must not appear as a title.
        if (videoFiles(files).isEmpty()) return null
        val title = textOf(meta, "title") ?: id
        return ProviderMedia(
            id = id,
            title = clean(title),
            contentType = if (isSeries(meta)) "series" else "movie",
            originalTitle = textOf(meta, "title_alt")?.let(::clean),
            releaseYear = yearOf(meta.opt("year")) ?: yearOf(meta.opt("date")) ?: yearOf(meta.opt("publicdate")),
            posterUrl = posterUrl(id),
            description = textOf(meta, "description")?.let(::clean)?.take(DESCRIPTION_LIMIT),
            genres = subjectsOf(meta),
            status = "resolved",
            rating = ratingOf(meta),
            createdAt = textOf(meta, "addeddate"),
            updatedAt = textOf(meta, "publicdate") ?: textOf(meta, "addeddate"),
            canonicalKey = id,
        )
    }

    /**
     * A TV item of archive.org is one episode, so it exposes a single season. Films expose no
     * structure at all and the details screen keeps its movie layout.
     */
    override fun seasons(contentId: String): List<ProviderSeason> {
        val root = metadata(contentId) ?: return emptyList()
        val meta = root.optJSONObject("metadata") ?: return emptyList()
        val files = root.optJSONArray("files") ?: return emptyList()
        if (!isSeries(meta) || videoFiles(files).isEmpty()) return emptyList()
        return listOf(ProviderSeason(seasonNumber = 1, name = "Season 1", sortOrder = 0))
    }

    /**
     * Episodes are grouped by the source file they are derived from: archive.org keeps the original
     * next to its derivations (`x.mp4`, `x.ia.mp4`, `x_512kb.mp4`), and those are qualities of one
     * episode, not separate episodes.
     */
    override fun episodes(contentId: String): List<ProviderEpisode> {
        val root = metadata(contentId) ?: return emptyList()
        val meta = root.optJSONObject("metadata") ?: return emptyList()
        val files = root.optJSONArray("files") ?: return emptyList()
        if (!isSeries(meta)) return emptyList()
        val groups = episodeGroups(files)
        if (groups.isEmpty()) return emptyList()
        val itemTitle = textOf(meta, "title")?.let(::clean)?.takeIf { it.isNotBlank() }
        // archive.org names episode files after the episode (`s01e04_ThroneForALoss.mp4`), so the
        // number and the label come from there instead of the position in the response.
        val labels = groups.map(::episodeLabel)
        val numbers = uniqueNumbers(labels.mapIndexed { index, label -> episodeNumber(label, index + 1) })
        return groups.mapIndexed { index, group ->
            val label = labels[index]
            ProviderEpisode(
                id = group.key,
                seasonNumber = 1,
                episodeNumber = numbers[index],
                title = when {
                    groups.size == 1 -> itemTitle ?: label
                    else -> label ?: "Episode ${numbers[index]}"
                },
                description = null,
                posterUrl = posterUrl(contentId),
                sortOrder = numbers[index],
            )
        }.sortedBy { it.episodeNumber }
    }

    /**
     * Every video file of the item is a playable row: archive.org keeps an original plus derived
     * MP4s, so the quality and server sheets get real choices instead of one hardcoded link.
     */
    override fun sources(contentId: String, episode: ProviderEpisode?): List<ProviderSource> {
        val root = metadata(contentId) ?: return emptyList()
        val files = root.optJSONArray("files") ?: return emptyList()
        val playable = if (episode == null) {
            videoFiles(files)
        } else {
            episodeGroups(files).firstOrNull { it.key == episode.id }?.files ?: videoFiles(files)
        }
        return playable.mapNotNull { file ->
            val fileName = file.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = "$BASE/download/${ProviderHttp.encodePath(contentId)}/${ProviderHttp.encodePath(fileName)}"
            val quality = heightOf(file)
            ProviderSource(
                serverName = "Internet Archive • ${quality?.let { "${it}p" } ?: "Auto"}",
                url = url,
                linkType = "VIDEO",
                quality = quality,
                codec = codecOf(file),
                language = "Original",
                isDub = false,
            )
        }.distinctBy { it.url }
    }

    // ---------------------------------------------------------------------------------------------
    // archive.org reads
    // ---------------------------------------------------------------------------------------------

    private fun listing(collection: String, contentType: String): List<ProviderMedia> {
        val docs = queryDocs(
            params = listOf(
                "q" to "collection:($collection) AND mediatype:movies",
            ),
            rows = LISTING_ROWS,
            sort = "downloads desc",
        )
        return docs.mapNotNull { doc -> docToMedia(doc, contentType) }
    }

    private fun queryDocs(
        params: List<Pair<String, String>>,
        rows: Int,
        sort: String,
    ): List<JSONObject> {
        val url = ProviderHttp.buildUrl(
            "$BASE/advancedsearch.php",
            params + listOf(
                "fl[]" to "identifier",
                "fl[]" to "title",
                "fl[]" to "year",
                "fl[]" to "date",
                "fl[]" to "subject",
                "fl[]" to "description",
                "fl[]" to "collection",
                "fl[]" to "publicdate",
                "fl[]" to "addeddate",
                "fl[]" to "avg_rating",
                "rows" to rows.toString(),
                "page" to "1",
                "output" to "json",
                "sort[]" to sort,
            ),
        )
        val body = ProviderHttp.getText(url)
        val response = JSONObject(body).optJSONObject("response") ?: return emptyList()
        val docs = response.optJSONArray("docs") ?: return emptyList()
        return buildList {
            for (index in 0 until docs.length()) {
                docs.optJSONObject(index)?.let { add(it) }
            }
        }
    }

    private fun metadata(identifier: String): JSONObject? {
        if (identifier.isBlank()) return null
        synchronized(metadataCache) { metadataCache[identifier] }?.let { return it }
        val body = ProviderHttp.getText("$BASE/metadata/${ProviderHttp.encodePath(identifier)}")
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optJSONObject("metadata") == null) return null
        synchronized(metadataCache) {
            metadataCache[identifier] = root
            while (metadataCache.size > CACHE_SIZE) {
                val oldest = metadataCache.keys.firstOrNull() ?: break
                metadataCache.remove(oldest)
            }
        }
        return root
    }

    /** Public to the module (not to plugins) so the mapping is covered by real unit tests. */
    internal fun docToMedia(
        doc: JSONObject,
        contentType: String = contentTypeOf(doc),
    ): ProviderMedia? {
        val identifier = doc.optString("identifier").takeIf { it.isNotBlank() } ?: return null
        val title = textOf(doc, "title") ?: identifier
        return ProviderMedia(
            id = identifier,
            title = clean(title),
            contentType = contentType,
            releaseYear = yearOf(doc.opt("year")) ?: yearOf(doc.opt("date")),
            posterUrl = posterUrl(identifier),
            description = textOf(doc, "description")?.let(::clean)?.take(DESCRIPTION_LIMIT),
            genres = subjectsOf(doc),
            status = "resolved",
            rating = ratingOf(doc),
            createdAt = textOf(doc, "addeddate"),
            updatedAt = textOf(doc, "publicdate") ?: textOf(doc, "addeddate"),
            canonicalKey = identifier,
        )
    }

    private fun posterUrl(identifier: String): String =
        "$BASE/services/img/${ProviderHttp.encodePath(identifier)}"

    /** A search document carries its collections when `fl[]=collection` was requested. */
    private fun contentTypeOf(doc: JSONObject): String =
        if (isSeries(doc)) "series" else "movie"

    private fun isSeries(json: JSONObject): Boolean {
        val collections = textOf(json, "collection") ?: return false
        return collections.split(';')
            .map { it.trim().lowercase() }
            .any { it in TV_COLLECTIONS }
    }

    internal data class EpisodeGroup(val key: String, val files: List<JSONObject>)

    /**
     * Groups video files by the file they were derived from, so one episode keeps its qualities
     * together instead of showing up several times in the episode list.
     */
    internal fun episodeGroups(files: JSONArray): List<EpisodeGroup> = videoFiles(files)
        .groupBy { file -> renditionKey(file.optString("name")) }
        .toSortedMap()
        .map { (key, group) -> EpisodeGroup(key, group) }

    /** The file name of one episode without its extension, as a display label. */
    internal fun episodeLabel(group: EpisodeGroup): String? = group.files
        .firstOrNull()
        ?.optString("name")
        ?.substringBeforeLast('.')
        ?.replace('_', ' ')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    /** Reads `s01e04`/`e4`/`episode 4` out of a label, falling back to the position. */
    internal fun episodeNumber(label: String?, fallback: Int): Int =
        label?.let { EPISODE_PATTERN.find(it.lowercase())?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: fallback

    /**
     * Two files can carry the same episode number (`s01e01.mp4`, `s01e01-extra.mp4`); the details
     * screen keys episodes by number, so a duplicate would hide one of them.
     */
    internal fun uniqueNumbers(raw: List<Int>): List<Int> {
        val used = mutableSetOf<Int>()
        var next = (raw.maxOrNull() ?: 0) + 1
        return raw.map { number ->
            if (used.add(number)) {
                number
            } else {
                while (!used.add(next)) next++
                next++
                next - 1
            }
        }
    }

    internal fun renditionKey(fileName: String): String = fileName
        .substringBeforeLast('.')
        .lowercase()
        .replace(DERIVED_MARKERS, "")
        .trim()

    // ---------------------------------------------------------------------------------------------
    // Parsing helpers
    // ---------------------------------------------------------------------------------------------

    /** Reads a field that archive.org returns either as a string or as an array of strings. */
    private fun textOf(json: JSONObject, key: String): String? {
        val value = json.opt(key) ?: return null
        if (value == JSONObject.NULL) return null
        if (value is JSONArray) {
            val joined = buildList {
                for (index in 0 until value.length()) {
                    val part = value.opt(index)?.toString()?.takeIf { it.isNotBlank() } ?: continue
                    add(part)
                }
            }.joinToString("; ")
            return joined.takeIf { it.isNotBlank() }
        }
        return value.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun subjectsOf(json: JSONObject): List<String> {
        val raw = textOf(json, "subject") ?: return emptyList()
        return raw.split(';', ',')
            .map { clean(it) }
            .filter { it.isNotBlank() && it.length <= 40 }
            .distinct()
            .take(MAX_GENRES)
    }

    private fun yearOf(raw: Any?): Int? = when (raw) {
        null, JSONObject.NULL -> null
        is Number -> raw.toInt().takeIf { it in MIN_YEAR..MAX_YEAR }
        else -> Regex("(1[89]\\d{2}|20\\d{2})")
            .find(raw.toString())?.value?.toIntOrNull()
            ?.takeIf { it in MIN_YEAR..MAX_YEAR }
    }

    private fun ratingOf(json: JSONObject): Double? {
        val raw = json.opt("avg_rating")
        val value = when (raw) {
            null, JSONObject.NULL -> return null
            is Number -> raw.toDouble()
            else -> raw.toString().toDoubleOrNull() ?: return null
        }
        return value.takeIf { it > 0.0 && it <= 5.0 }?.let { (it * 10).toInt() / 10.0 }
    }

    private fun videoFiles(files: JSONArray): List<JSONObject> {
        val videos = buildList {
            for (index in 0 until files.length()) {
                val file = files.optJSONObject(index) ?: continue
                val fileName = file.optString("name")
                val extension = fileName.substringAfterLast('.', "").lowercase()
                if (extension !in VIDEO_EXTENSIONS) continue
                if (file.optString("private").equals("true", ignoreCase = true)) continue
                add(file)
            }
        }
        return videos.sortedByDescending { heightOf(it) ?: 0 }
    }

    private fun heightOf(file: JSONObject): Int? {
        val raw = file.opt("height") ?: return null
        if (raw == JSONObject.NULL) return null
        val height = when (raw) {
            is Number -> raw.toInt()
            else -> raw.toString().toIntOrNull() ?: return null
        }
        return height.takeIf { it in MIN_HEIGHT..MAX_HEIGHT }
    }

    private fun codecOf(file: JSONObject): String? {
        val haystack = file.optString("format") + " " + file.optString("name")
        return when {
            haystack.contains("h.264", true) || haystack.contains("h264", true) -> "H264"
            haystack.contains("h.265", true) || haystack.contains("hevc", true) -> "HEVC"
            haystack.contains("av1", true) -> "AV1"
            else -> null
        }
    }

    /** Drops HTML from archived descriptions and collapses the whitespace. */
    private fun clean(text: String): String = text
        .replace(Regex("<[^>]{0,400}>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val BASE = "https://archive.org"
        const val CACHE_SIZE = 64
        const val LISTING_ROWS = 200
        const val SEARCH_ROWS = 60
        const val DESCRIPTION_LIMIT = 2000
        const val MAX_GENRES = 4
        const val MIN_YEAR = 1888
        const val MAX_YEAR = 2100
        const val MIN_HEIGHT = 120
        const val MAX_HEIGHT = 4320
        const val QUERY_SPECIALS = "[:\"\\\\()\\[\\]{}^~]"

        /** Collections that are public domain or openly licensed, and hold playable video. */
        val MOVIE_COLLECTIONS = listOf("feature_films", "classic_cartoons", "short_films")

        /** Public-domain television: archive.org stores one episode per item. */
        val TV_COLLECTIONS = listOf("classic_tv")

        val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "ogv")

        /** `s01e04`, `e4`, `ep 4`, `episode 4` — the group holds the number only. */
        val EPISODE_PATTERN = Regex("(?:^|[^0-9])(?:s[0-9]{1,2}[\\s._-]*)?e(?:p|pisode)?[\\s._-]*([0-9]{1,3})")

        /** Suffixes archive.org adds to derived files (`x.ia.mp4`, `x_512kb.mp4`, ...). */
        val DERIVED_MARKERS = Regex("(_\\d+kb|\\.ia|_ia|_mpeg4|_derived\\w*)")
    }
}
