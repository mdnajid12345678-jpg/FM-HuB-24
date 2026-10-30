package com.fmhub24.android.data.repo

import android.content.Context
import android.os.StatFs
import com.fmhub24.android.data.local.DownloadEntity
import com.fmhub24.android.data.local.DownloadsDao
import com.fmhub24.android.data.model.ContentType
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.stream.DownloadAnalyzer
import com.fmhub24.android.stream.DownloadEngine
import com.fmhub24.android.stream.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A download row: Room metadata plus the live Media3 state when it is known. */
data class DownloadRow(
    val record: DownloadEntity,
    val state: DownloadState?,
) {
    val isFinished: Boolean
        get() = state?.phase == DownloadState.Phase.COMPLETED
    val isFailed: Boolean
        get() = state?.phase == DownloadState.Phase.FAILED
    val isInProgress: Boolean
        get() = state?.phase == DownloadState.Phase.DOWNLOADING ||
            state?.phase == DownloadState.Phase.QUEUED ||
            state?.phase == DownloadState.Phase.PAUSED
    val percent: Float
        get() = state?.percent ?: 0f
    val sizeBytes: Long?
        get() = state?.bytesTotal?.takeIf { it > 0 } ?: record.sizeBytes
    /** For series grouping in the Downloads tab. */
    val groupKey: String get() = record.contentId
}

/** Options offered in the download sheet after the "Checking" step (spec 6.8). */
data class DownloadOptions(
    val targetId: String,
    val episodeKey: String?,
    /** Fresh, resolved sources for this target; never cached. */
    val sources: List<MediaSource>,
    /** Populated for multi-language HLS so audio tracks can be ticked. */
    val hlsAnalysis: DownloadAnalyzer.Analysis?,
    val freeSpaceBytes: Long,
)

/**
 * Metadata for Room, commands to Media3, and the size estimate from Content-Length.
 * The signed link is read fresh at download start and never persisted (spec 5.2 rule 3).
 */
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadsDao,
    private val http: OkHttpClient,
) {
    val rows: Flow<List<DownloadRow>> =
        combine(dao.observeAll(), DownloadEngine.states) { records, states ->
            records.map { record ->
                DownloadRow(record = record, state = states[record.id])
            }
        }

    suspend fun records(): List<DownloadEntity> = dao.all()

    suspend fun recordFor(contentId: String, episodeKey: String?): DownloadEntity? =
        dao.all().firstOrNull {
            it.contentId == contentId && it.episodeKey == episodeKey
        }

    suspend fun recordWithId(id: String): DownloadEntity? = dao.byId(id)

    suspend fun offlineSource(contentId: String, episodeKey: String?): MediaSource? =
        withContext(Dispatchers.IO) {
            dao.all().asSequence().filter {
                it.contentId == contentId && it.episodeKey == episodeKey
            }.mapNotNull { record ->
                val request = DownloadEngine.completedRequest(context, record.id) ?: return@mapNotNull null
                val uri = request.uri.toString()
                val hls = request.mimeType.equals("application/x-mpegURL", ignoreCase = true) ||
                    uri.substringBefore("?").endsWith(".m3u8", ignoreCase = true)
                MediaSource(
                    id = record.id,
                    contentId = contentId,
                    episodeId = episodeKey,
                    seasonNumber = record.seasonNumber,
                    episodeNumber = record.episodeNumber,
                    serverName = record.serverName,
                    mediaUrl = uri,
                    quality = record.quality,
                    linkType = if (hls) "M3U8" else "VIDEO",
                    status = "offline",
                    headers = com.fmhub24.android.stream.StreamEngine.decodeHeaders(request.data),
                    columnLanguage = record.language,
                    columnIsDub = null,
                    columnCodec = null,
                    resolvedAt = null,
                    expiresAt = null,
                )
            }.firstOrNull()
        }

    suspend fun hasDownload(contentId: String, episodeKey: String?): Boolean =
        dao.all().any { it.contentId == contentId && it.episodeKey == episodeKey }

    /** Build the id used by both Room and Media3 for one version of one target. */
    fun downloadId(contentId: String, episodeKey: String?, source: MediaSource): String {
        val target = episodeKey ?: contentId
        return "$target@${source.quality ?: "auto"}@${source.serverLabel.hashCode()}"
    }

    suspend fun start(
        item: MediaItem,
        episode: EpisodeItem?,
        source: MediaSource,
        headers: Map<String, String>,
        maxHeight: Int? = null,
        audioLanguages: List<String> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val episodeKey = episode?.key
        val id = downloadId(item.id, episodeKey, source)
        val request = DownloadAnalyzer.buildRequest(
            context = context,
            id = id,
            source = source,
            headers = headers,
            maxHeight = maxHeight,
            audioLanguages = audioLanguages,
        )
        dao.upsert(
            DownloadEntity(
                id = id,
                contentId = item.id,
                episodeKey = episodeKey,
                seasonNumber = episode?.seasonNumber,
                episodeNumber = episode?.episodeNumber,
                title = if (episode == null) item.title else buildString {
                    append("${item.title} · S${episode.seasonNumber}E${episode.episodeNumber}")
                    episode.title?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                posterUrl = episode?.posterUrl ?: item.posterUrl,
                contentType = item.contentType.raw,
                serverName = source.serverLabel,
                language = source.language,
                quality = source.quality,
                sizeBytes = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        DownloadEngine.add(context, request)
        id
    }

    /** Content-Length of a direct file; null when the host refuses HEAD or sends no length. */
    suspend fun estimateSize(url: String, headers: Map<String, String>): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                val builder = Request.Builder().url(url).head()
                headers.forEach { (name, value) -> builder.header(name, value) }
                http.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                }
            }.getOrNull()
        }

    fun setPaused(id: String, paused: Boolean) = DownloadEngine.setPaused(context, id, paused)

    fun pauseAll() = DownloadEngine.pauseAll(context)

    fun resumeAll() = DownloadEngine.resumeAll(context)

    suspend fun remove(id: String) {
        DownloadEngine.remove(context, id)
        dao.delete(id)
    }

    suspend fun removeTarget(contentId: String, episodeKey: String?) {
        val targets = dao.all().filter {
            it.contentId == contentId && (episodeKey == null || it.episodeKey == episodeKey)
        }
        targets.forEach { DownloadEngine.remove(context, it.id) }
        if (episodeKey == null) dao.deleteByContentId(contentId)
        else dao.deleteByTarget(contentId, episodeKey)
    }

    /** Free bytes on the volume that holds app-private storage. */
    fun freeSpaceBytes(): Long = runCatching {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    }.getOrDefault(0L)

    fun isDeletingAllowed(contentType: ContentType): Boolean = true
}
