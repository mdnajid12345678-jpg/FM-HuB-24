package com.fmhub24.android.data.repo

import com.fmhub24.android.data.model.DataException
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.provider.ContentProvider
import com.fmhub24.android.data.provider.ProviderRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live TV comes from a provider that declares `supportsLive` (the public iptv-org index by
 * default), and is intentionally independent of the on-demand catalog.
 */
@Singleton
class LiveTvRepository @Inject constructor(
    private val providers: ProviderRegistry,
) {
    suspend fun channels(): List<MediaItem> = liveProvider().liveChannels()

    /** Fresh, resolved links for one channel. */
    suspend fun sources(channelId: String): List<MediaSource> =
        liveProvider().liveSources(channelId).filter { it.isPlayable }

    private fun liveProvider(): ContentProvider =
        providers.liveProvider() ?: throw DataException.NotConfigured()
}
