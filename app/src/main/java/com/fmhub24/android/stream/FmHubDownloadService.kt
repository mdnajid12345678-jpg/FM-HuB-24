@file:OptIn(UnstableApi::class)

package com.fmhub24.android.stream

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.fmhub24.android.R

/**
 * Foreground service that runs Media3 downloads with a progress notification (spec 6.8).
 * All state lives in [DownloadEngine]; this class only adapts it to the service contract.
 * Media3 starts the service with FOREGROUND_SERVICE_TYPE_DATA_SYNC itself.
 */
class FmHubDownloadService : DownloadService(
    DownloadEngine.NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadEngine.CHANNEL_ID,
    R.string.notification_channel_downloads,
    R.string.notification_channel_downloads,
) {
    override fun getDownloadManager(): DownloadManager = DownloadEngine.manager(applicationContext)

    override fun getScheduler(): Scheduler? =
        PlatformScheduler(applicationContext, DownloadEngine.SCHEDULER_JOB_ID)

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification = DownloadEngine.foregroundNotification(this, downloads, notMetRequirements)
}
