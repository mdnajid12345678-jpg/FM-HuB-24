@file:OptIn(UnstableApi::class)

package com.fmhub24.android.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import androidx.media3.exoplayer.offline.WritableDownloadIndex
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.fmhub24.android.MainActivity
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One row of live download state, mirrored from Media3 for the Downloads screen. */
data class DownloadState(
    val id: String,
    val phase: Phase,
    val percent: Float,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val hasFailed: Boolean,
) {
    enum class Phase { UNKNOWN, QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED, REMOVING }
}

/**
 * Owns the single Media3 [DownloadManager] for the app (the download service must be given one),
 * the on-disk cache in app-private storage, and the live state flow the UI reads.
 *
 * Nothing here decides policy: headers come from the request, progress comes from Media3, and the
 * Room table only holds the metadata needed to draw the list.
 */
object DownloadEngine {
    const val CHANNEL_ID = "fmhub24_downloads"
    const val NOTIFICATION_ID = 4400
    const val SCHEDULER_JOB_ID = 4401

    /** App-defined stop reason used for the per-item pause button (0 means "run again"). */
    const val STOP_REASON_PAUSED_BY_USER = 100

    private val initialisationLock = Any()
    @Volatile private var ready = false

    private lateinit var databaseProvider: StandaloneDatabaseProvider
    private lateinit var cache: SimpleCache
    private lateinit var downloadIndex: WritableDownloadIndex
    private lateinit var downloaderFactory: DownloaderFactory
    private lateinit var downloadManager: DownloadManager

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states

    fun init(context: Context) {
        if (ready) return
        synchronized(initialisationLock) {
            if (ready) return
            val app = context.applicationContext
            createChannel(app)
            databaseProvider = StandaloneDatabaseProvider(app)
            cache = SimpleCache(
                File(app.filesDir, "fmhub24_downloads"),
                // Downloads are user data: they are only removed by an explicit delete.
                NoOpCacheEvictor(),
                databaseProvider,
            )
            downloadIndex = DefaultDownloadIndex(databaseProvider)
            downloaderFactory = DownloaderFactory { request -> downloaderFor(request) }
            downloadManager = DownloadManager(app, downloadIndex, downloaderFactory).apply {
                maxParallelDownloads = 2
                addListener(listener)
            }
            ready = true
        }
    }

    fun manager(context: Context): DownloadManager {
        init(context)
        return downloadManager
    }

    fun configure(context: Context, wifiOnly: Boolean, parallelDownloads: Int) {
        init(context)
        downloadManager.maxParallelDownloads = parallelDownloads.coerceIn(1, 5)
        downloadManager.setRequirements(
            if (wifiOnly) Requirements(Requirements.NETWORK_UNMETERED) else Requirements(0),
        )
    }

    fun completedRequest(context: Context, id: String): DownloadRequest? = runCatching {
        init(context)
        downloadIndex.getDownload(id)
            ?.takeIf { it.state == Download.STATE_COMPLETED }
            ?.request
    }.getOrNull()

    fun cacheDataSourceFactory(): DataSource.Factory {
        check(ready) { "Download cache has not been initialized" }
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // ------------------------------------------------------------------------------------------
    // Commands (all routed through the service so state stays in one place)
    // ------------------------------------------------------------------------------------------

    fun add(context: Context, request: DownloadRequest) {
        init(context)
        DownloadService.sendAddDownload(
            context.applicationContext,
            FmHubDownloadService::class.java,
            request,
            false,
        )
    }

    fun remove(context: Context, id: String) {
        init(context)
        DownloadService.sendRemoveDownload(
            context.applicationContext,
            FmHubDownloadService::class.java,
            id,
            false,
        )
    }

    fun setPaused(context: Context, id: String, paused: Boolean) {
        init(context)
        DownloadService.sendSetStopReason(
            context.applicationContext,
            FmHubDownloadService::class.java,
            id,
            if (paused) STOP_REASON_PAUSED_BY_USER else Download.STOP_REASON_NONE,
            false,
        )
    }

    fun pauseAll(context: Context) {
        init(context)
        DownloadService.sendPauseDownloads(
            context.applicationContext,
            FmHubDownloadService::class.java,
            false,
        )
    }

    fun resumeAll(context: Context) {
        init(context)
        DownloadService.sendResumeDownloads(
            context.applicationContext,
            FmHubDownloadService::class.java,
            false,
        )
    }

    // ------------------------------------------------------------------------------------------
    // Notification shown while the service runs in the foreground
    // ------------------------------------------------------------------------------------------

    fun foregroundNotification(
        context: Context,
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        init(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Spec 6.8: the progress notification carries "Pause all" and "Cancel".
        val pauseIntent = PendingIntent.getService(
            context,
            1,
            DownloadService.buildPauseDownloadsIntent(
                context,
                FmHubDownloadService::class.java,
                true,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelTarget = downloads.firstOrNull {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        val cancelIntent = cancelTarget?.let {
            val cancelAction = DownloadService.buildRemoveDownloadIntent(
                context,
                FmHubDownloadService::class.java,
                it.request.id,
                true,
            )
            PendingIntent.getService(
                context,
                2,
                cancelAction,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val downloading = downloads.count {
            it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        val activeDownloads = downloads.filter {
                it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED
        }
        val knownSizeDownloads = activeDownloads.filter { it.contentLength > 0L }
        val percent = if (knownSizeDownloads.isNotEmpty()) {
            val totalBytes = knownSizeDownloads.sumOf { it.contentLength }
            (knownSizeDownloads.sumOf { it.getBytesDownloaded() }.toDouble() /
                totalBytes.toDouble() * 100.0).toFloat().coerceIn(0f, 100f)
        } else if (downloading > 0) {
            activeDownloads.map { it.getPercentDownloaded().takeIf { value -> value >= 0f } ?: 0f }
                .average().toFloat().coerceIn(0f, 100f)
        } else 0f

        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(com.fmhub24.android.R.string.app_name))
            .setContentText(
                context.getString(
                    com.fmhub24.android.R.string.download_in_progress,
                    percent.toInt(),
                ),
            )
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    0,
                    context.getString(com.fmhub24.android.R.string.action_pause),
                    pauseIntent,
                ).build(),
            )
            .apply {
                cancelIntent?.let { cancel -> addAction(
                Notification.Action.Builder(
                    0,
                    context.getString(com.fmhub24.android.R.string.action_cancel_download),
                    cancel,
                ).build(),
                ) }
            }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder.setProgress(100, percent.toInt(), downloading > 0 && percent <= 0f)
        }
        if (notMetRequirements != 0) {
            builder.setContentText(
                context.getString(com.fmhub24.android.R.string.state_network_error),
            )
        }
        return builder.build()
    }

    // ------------------------------------------------------------------------------------------
    // Downloader creation: every request carries its own headers
    // ------------------------------------------------------------------------------------------

    private fun downloaderFor(request: DownloadRequest): Downloader {
        val headers = StreamEngine.decodeHeaders(request.data)
        val upstream = StreamEngine.dataSourceFactory(headers)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
        val mediaItem = request.toMediaItem()
        val isHls = request.mimeType == "application/x-mpegURL" ||
            request.uri.toString().substringBefore("?").endsWith(".m3u8", ignoreCase = true)
        return if (isHls) {
            HlsDownloader(mediaItem, cacheFactory)
        } else {
            ProgressiveDownloader(mediaItem, cacheFactory)
        }
    }

    // ------------------------------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------------------------------

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(manager: DownloadManager) {
            publish(manager.currentDownloads)
        }

        override fun onDownloadChanged(
            manager: DownloadManager,
            download: Download,
            exception: Exception?,
        ) {
            publish(listOf(download))
        }

        override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
            _states.value = _states.value - download.request.id
        }
    }

    private fun publish(downloads: List<Download>) {
        if (downloads.isEmpty()) return
        val updated = _states.value.toMutableMap()
        downloads.forEach { download -> updated[download.request.id] = download.toState() }
        _states.value = updated
    }

    private fun Download.toState(): DownloadState = DownloadState(
        id = request.id,
        phase = when (state) {
            Download.STATE_QUEUED -> DownloadState.Phase.QUEUED
            Download.STATE_DOWNLOADING -> DownloadState.Phase.DOWNLOADING
            Download.STATE_COMPLETED -> DownloadState.Phase.COMPLETED
            Download.STATE_FAILED -> DownloadState.Phase.FAILED
            Download.STATE_STOPPED -> DownloadState.Phase.PAUSED
            Download.STATE_REMOVING -> DownloadState.Phase.REMOVING
            Download.STATE_RESTARTING -> DownloadState.Phase.QUEUED
            else -> DownloadState.Phase.UNKNOWN
        },
        percent = if (contentLength > 0) {
            (getPercentDownloaded() / 100f).coerceIn(0f, 1f)
        } else {
            0f
        },
        bytesDownloaded = getBytesDownloaded(),
        bytesTotal = contentLength.coerceAtLeast(0L),
        hasFailed = failureReason != Download.FAILURE_REASON_NONE,
    )

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(com.fmhub24.android.R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(com.fmhub24.android.R.string.notification_channel_downloads)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
