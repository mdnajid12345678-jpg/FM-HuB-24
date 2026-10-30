@file:OptIn(UnstableApi::class)

package com.fmhub24.android.stream

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.fmhub24.android.data.model.MediaSource
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Uses Media3's DownloadHelper to prepare a source, list its tracks and turn the user's choice
 * into a DownloadRequest (spec section 7).
 */
object DownloadAnalyzer {
    data class VideoOption(
        val height: Int,
        val bitrate: Int,
        val supported: Boolean,
    )

    data class AudioOption(
        val language: String?,
        val label: String?,
        val supported: Boolean,
    )

    data class Analysis(
        val video: List<VideoOption>,
        val audio: List<AudioOption>,
        val isLive: Boolean,
    ) {
        val hasVariants: Boolean get() = video.size > 1
    }

    /**
     * Prepares the stream and reads its own track list. Runs on the main dispatcher because
     * DownloadHelper delivers its callback on the caller's Looper.
     */
    suspend fun analyze(
        context: Context,
        source: MediaSource,
        headers: Map<String, String>,
    ): Analysis = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val helper = DownloadHelper.forMediaItem(
                context,
                StreamEngine.mediaItem(source, source.id ?: source.mediaUrl),
                StreamEngine.dataSourceFactory(headers),
            )
            continuation.invokeOnCancellation { helper.release() }
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper, isLive: Boolean) {
                    val analysis = readTracks(helper, isLive)
                    helper.release()
                    if (continuation.isActive) continuation.resume(analysis)
                }

                override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                    helper.release()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            })
        }
    }

    /**
     * Builds the request that is handed to DownloadService. Direct VIDEO files need no track
     * analysis; HLS picks the chosen video height and audio languages before the request is built.
     */
    suspend fun buildRequest(
        context: Context,
        id: String,
        source: MediaSource,
        headers: Map<String, String>,
        maxHeight: Int? = null,
        audioLanguages: List<String> = emptyList(),
    ): DownloadRequest {
        val data = StreamEngine.encodeHeaders(headers)
        val isHls = StreamEngine.mimeTypeFor(source) == "application/x-mpegURL"
        if (!isHls) {
            return DownloadRequest.Builder(id, Uri.parse(source.mediaUrl))
                .setMimeType(StreamEngine.mimeTypeFor(source))
                .setData(data)
                .build()
        }

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val helper = DownloadHelper.forMediaItem(
                    context,
                    StreamEngine.mediaItem(source, id),
                    StreamEngine.dataSourceFactory(headers),
                )
                continuation.invokeOnCancellation { helper.release() }
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper, isLive: Boolean) {
                        try {
                            if (helper.periodCount > 0) {
                                val parameters = DefaultTrackSelector.Parameters.DEFAULT
                                    .buildUpon()
                                if (maxHeight != null && maxHeight > 0) {
                                    parameters.setMaxVideoSize(Int.MAX_VALUE, maxHeight)
                                }
                                if (audioLanguages.isNotEmpty()) {
                                    parameters.setPreferredAudioLanguages(
                                        *audioLanguages.toTypedArray(),
                                    )
                                }
                                helper.replaceTrackSelections(0, parameters.build())
                            }
                            val request = helper.getDownloadRequest(id, data)
                            helper.release()
                            if (continuation.isActive) continuation.resume(request)
                        } catch (error: Throwable) {
                            helper.release()
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }

                    override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                        helper.release()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                })
            }
        }
    }

    /** Creates an offline request containing the selected HLS text language only. */
    suspend fun buildSubtitleRequest(
        context: Context,
        id: String,
        source: MediaSource,
        headers: Map<String, String>,
        language: String,
    ): DownloadRequest? {
        if (StreamEngine.mimeTypeFor(source) != "application/x-mpegURL") return null
        val data = StreamEngine.encodeHeaders(headers)
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val helper = DownloadHelper.forMediaItem(
                    context,
                    StreamEngine.mediaItem(source, id),
                    StreamEngine.dataSourceFactory(headers),
                )
                continuation.invokeOnCancellation { helper.release() }
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper, isLive: Boolean) {
                        try {
                            if (helper.periodCount <= 0) {
                                helper.release()
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            val parameters = DefaultTrackSelector.Parameters.DEFAULT.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setPreferredTextLanguages(language)
                                .build()
                            helper.clearTrackSelections(0)
                            helper.replaceTrackSelections(0, parameters)
                            val request = helper.getDownloadRequest(id, data)
                                .takeIf { it.streamKeys.isNotEmpty() }
                            helper.release()
                            if (continuation.isActive) continuation.resume(request)
                        } catch (error: Throwable) {
                            helper.release()
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }

                    override fun onPrepareError(helper: DownloadHelper, error: IOException) {
                        helper.release()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                })
            }
        }
    }

    private fun readTracks(helper: DownloadHelper, isLive: Boolean): Analysis {
        if (helper.periodCount <= 0) return Analysis(emptyList(), emptyList(), isLive)
        val tracks: Tracks = helper.getTracks(0)
        val video = mutableListOf<VideoOption>()
        val audio = mutableListOf<AudioOption>()

        tracks.groups.forEach { group ->
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        video += VideoOption(
                            height = format.height,
                            bitrate = format.bitrate,
                            supported = group.isTrackSupported(index),
                        )
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (index in 0 until group.length) {
                        val format = group.getTrackFormat(index)
                        audio += AudioOption(
                            language = format.language,
                            label = format.label,
                            supported = group.isTrackSupported(index),
                        )
                    }
                }
            }
        }

        return Analysis(
            video = video.filter { it.height > 0 }.distinctBy { it.height }
                .sortedBy { it.height },
            audio = audio.distinctBy { it.language to it.label },
            isLive = isLive,
        )
    }
}
