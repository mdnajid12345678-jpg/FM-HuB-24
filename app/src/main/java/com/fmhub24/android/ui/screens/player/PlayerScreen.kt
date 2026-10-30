@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.fmhub24.android.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.fmhub24.android.R
import com.fmhub24.android.MainActivity
import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.ui.FmHubLoader
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.formatClock
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.stream.StreamEngine
import com.fmhub24.android.stream.StreamError
import com.fmhub24.android.stream.DownloadEngine
import kotlinx.coroutines.delay
import java.util.Locale

private enum class SeekRipple { LEFT, RIGHT }
private enum class PlayerPanel { AUDIO, SERVERS, QUALITY, EPISODES, SPEED, SETTINGS, STYLE, DELAY }
private enum class DragSide { LEFT, RIGHT }

private data class TrackChoice(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String?,
    val languageCode: String?,
    val selected: Boolean,
)

private fun trackChoices(tracks: Tracks, type: Int): List<TrackChoice> = buildList {
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != type) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            val format = group.getTrackFormat(trackIndex)
            val label = displayLanguage(format.language)
                ?: format.label?.trim()?.takeIf {
                    it.isNotEmpty() && !it.matches(Regex("(?i)^(audio|subtitle|track|stream)\\s*#?\\d+$"))
                }
            add(
                TrackChoice(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = label,
                    languageCode = format.language,
                    selected = group.isTrackSelected(trackIndex),
                ),
            )
        }
    }
}

private fun displayLanguage(rawLanguage: String?): String? {
    val tag = rawLanguage?.trim()?.replace('_', '-')?.takeIf {
        it.isNotEmpty() && !it.equals("und", true) && !it.equals("zxx", true)
    } ?: return null
    val code = tag.substringBefore('-').lowercase(Locale.ROOT)
    val known = mapOf(
        "en" to "English", "eng" to "English",
        "bn" to "Bengali", "ben" to "Bengali",
        "hi" to "Hindi", "hin" to "Hindi",
        "ta" to "Tamil", "tam" to "Tamil",
        "te" to "Telugu", "tel" to "Telugu",
        "ur" to "Urdu", "urd" to "Urdu",
        "ar" to "Arabic", "ara" to "Arabic",
        "ja" to "Japanese", "jpn" to "Japanese",
        "ko" to "Korean", "kor" to "Korean",
        "zh" to "Chinese", "zho" to "Chinese", "chi" to "Chinese",
        "fr" to "French", "fra" to "French", "fre" to "French",
        "es" to "Spanish", "spa" to "Spanish",
        "pt" to "Portuguese", "por" to "Portuguese",
        "ru" to "Russian", "rus" to "Russian",
    )
    return known[code] ?: Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
        .takeIf { it.isNotBlank() && !it.equals(code, true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

@Composable
private fun TrackChoice.label(fallbackResource: Int): String =
    label ?: stringRes(fallbackResource, trackIndex + 1)

/** Landscape player (spec 6.7): controls, gestures, panels, resume, failure and next episode. */
@Composable
fun PlayerScreen(
    contentId: String,
    episodeKey: String?,
    onBack: () -> Unit,
    onOpenEpisode: (String, String?, Boolean) -> Unit,
    initialFullscreen: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val savedPlaybackSpeed by viewModel.playbackSpeed.collectAsState(initial = 1f)
    val autoMiniplayer by viewModel.autoMiniplayer.collectAsState(initial = false)
    val savedSubtitleSize by viewModel.subtitleSize.collectAsState(initial = "medium")
    val subtitleDelayMs by viewModel.subtitleDelayMs.collectAsState(initial = 0)
    val bilingualSubtitles by viewModel.bilingualSubtitles.collectAsState(initial = false)
    val subtitleDownloadStatus by viewModel.subtitleDownloadStatus.collectAsState()
    val activity = LocalContext.current.findActivity()

    var fullscreen by remember(contentId) { mutableStateOf(initialFullscreen) }

    DisposableEffect(activity) {
        val window = activity?.window
        onDispose {
            window?.let {
                val controller = WindowCompat.getInsetsController(it, it.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            (activity as? MainActivity)?.setPlayerActive(false)
        }
    }

    LaunchedEffect(activity, state.source, state.loading, state.failure) {
        (activity as? MainActivity)?.setPlayerActive(
            active = !state.loading && state.source != null && state.failure == null,
        )
    }

    LaunchedEffect(activity, fullscreen) {
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (fullscreen) fullscreen = false else onBack()
    }

    val background = MaterialTheme.colorScheme.background

    when {
        state.loading && state.source == null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            FmHubLoader(Modifier.size(40.dp))
        }

        state.source == null || state.error != null -> FailureContent(
            background = background,
            message = if (state.error != null) {
                dataErrorMessage(state.error)
            } else {
                stringRes(R.string.no_playable_source)
            },
            alternatives = emptyList(),
            onTry = null,
            onReport = null,
            onBack = onBack,
        )

        else -> PlayerContent(
            state = state,
            viewModel = viewModel,
            savedPlaybackSpeed = savedPlaybackSpeed,
            autoMiniplayer = autoMiniplayer,
            savedSubtitleSize = savedSubtitleSize,
            subtitleDelayMs = subtitleDelayMs,
            bilingualSubtitles = bilingualSubtitles,
            subtitleDownloadStatus = subtitleDownloadStatus,
            isFullscreen = fullscreen,
            onToggleFullscreen = { fullscreen = !fullscreen },
            onBack = onBack,
            onOpenEpisode = onOpenEpisode,
        )
    }

    state.failure?.let { failure ->
        FailureOverlay(
            failure = failure,
            state = state,
            viewModel = viewModel,
            onBack = onBack,
        )
    }

    viewModel.reportResult.collectAsState().value?.let {
        ReportSentMessage(onDismiss = viewModel::dismissReport)
    }
}

@Composable
private fun PlayerContent(
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    savedPlaybackSpeed: Float,
    autoMiniplayer: Boolean,
    savedSubtitleSize: String,
    subtitleDelayMs: Int,
    bilingualSubtitles: Boolean,
    subtitleDownloadStatus: SubtitleDownloadStatus,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenEpisode: (String, String?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val activity = context.findActivity()
    val settingsPanelWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 336.dp.toPx() }
    val settingsPanelTopPx = with(androidx.compose.ui.platform.LocalDensity.current) { 58.dp.toPx() }
    val settingsPanelBottomPx = with(androidx.compose.ui.platform.LocalDensity.current) { 178.dp.toPx() }
    val source = state.source ?: return
    val headers = source.headers

    val player = remember(source.mediaUrl) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    if (source.status == "offline") DownloadEngine.cacheDataSourceFactory()
                    else StreamEngine.dataSourceFactory(headers),
                ),
            )
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build()
    }

    DisposableEffect(source.mediaUrl) {
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                viewModel.handlePlaybackError(
                    StreamEngine.classify(error),
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    val finalDuration = player.duration
                    if (finalDuration > 0L) viewModel.onPlaybackCompleted(finalDuration)
                    state.nextEpisode?.let { next ->
                        onOpenEpisode(state.item?.id.orEmpty(), next.key, isFullscreen)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (player.duration > 0L) {
                viewModel.recordProgress(player.currentPosition, player.duration)
            }
            player.removeListener(listener)
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE &&
                activity?.isInPictureInPictureMode != true
            ) {
                player.pause()
                if (player.duration > 0L) {
                    viewModel.recordProgress(player.currentPosition, player.duration)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var resumeAsked by remember { mutableStateOf(state.resumePositionMs < 5_000L) }
    var startAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, source.mediaUrl) {
        player.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        player.setMediaItem(
            androidx.media3.common.MediaItem.Builder()
                .setUri(source.mediaUrl)
                .setMimeType(StreamEngine.mimeTypeFor(source))
                .build(),
        )
        if (resumeAsked && state.resumePositionMs > 5_000L) {
            player.seekTo(state.resumePositionMs)
        }
        player.playWhenReady = true
        player.prepare()
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    val audioTracks = remember(tracks) { trackChoices(tracks, C.TRACK_TYPE_AUDIO) }
    val subtitleTracks = remember(tracks) { trackChoices(tracks, C.TRACK_TYPE_TEXT) }
    val audioLabel = audioTracks.firstOrNull { it.selected }?.let {
        it.label ?: stringRes(R.string.player_audio_track, it.trackIndex + 1)
    } ?: state.languageKey?.let { SourceSelection.languageLabel(it, false) }
        ?: stringRes(R.string.quality_auto)
    var lastSavedBucket by remember { mutableLongStateOf(0L) }
    var fitToScreen by remember(source.mediaUrl) { mutableStateOf(true) }
    val subtitleTextSize = when (savedSubtitleSize) {
        "small" -> 0.04f
        "large" -> 0.068f
        else -> SubtitleView.DEFAULT_TEXT_SIZE_FRACTION
    }
    val subtitleFontSize = when (savedSubtitleSize) {
        "small" -> 13.sp
        "large" -> 21.sp
        else -> 17.sp
    }
    var subtitleText by remember(player) { mutableStateOf("") }
    val delayedCueEvents = remember(player) { mutableListOf<Pair<Long, String>>() }

    DisposableEffect(player, subtitleDelayMs) {
        delayedCueEvents.clear()
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                val text = cueGroup.cues.mapNotNull { it.text?.toString() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                delayedCueEvents += (player.currentPosition + subtitleDelayMs.toLong()) to text
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, subtitleDelayMs) {
        while (true) {
            val now = player.currentPosition
            val dueEvents = delayedCueEvents.filter { it.first <= now }
            if (dueEvents.isNotEmpty()) {
                subtitleText = dueEvents.last().second
                delayedCueEvents.removeAll { it.first <= now }
            }
            delay(40)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0L } ?: 0L
            isPlaying = player.isPlaying
            isBuffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING
            tracks = player.currentTracks
            delay(500)
        }
    }

    LaunchedEffect(player, position, duration) {
        if (duration > 0L && position in 2_000L..duration && position / 5_000L != 0L) {
            val bucket = position / 5_000L
            if (bucket != lastSavedBucket) {
                lastSavedBucket = bucket
                viewModel.recordProgress(position, duration)
            }
        }
    }


    var showControls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }

    LaunchedEffect(startAt) {
        if (startAt > 0L) {
            delayedCueEvents.clear()
            subtitleText = ""
            player.seekTo(startAt)
        }
    }

    var speed by remember(savedPlaybackSpeed) { mutableFloatStateOf(savedPlaybackSpeed) }
    var gestureHint by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var seekRipple by remember { mutableStateOf<SeekRipple?>(null) }
    var reportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(player, fitToScreen) {
        player.videoScalingMode = if (fitToScreen) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
    }
    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(900)
            gestureHint = null
        }
    }
    LaunchedEffect(seekRipple) {
        if (seekRipple != null) {
            delay(450)
            seekRipple = null
        }
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val brightnessLabel = stringRes(R.string.player_brightness)
    val volumeLabel = stringRes(R.string.player_volume)
    val reportLabel = stringRes(R.string.report_problem)

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
    Box(
        modifier = (if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
            .aspectRatio(16f / 9f))
            .background(Color.Black)
            .pointerInput(locked, panel, isFullscreen) {
                detectTapGestures(
                    onTap = { offset ->
                        if (!locked) {
                            if (panel != null) {
                                val panelStart = if (panel == PlayerPanel.SETTINGS) {
                                    size.width - settingsPanelWidthPx
                                } else {
                                    val panelFraction = if (!isFullscreen) {
                                        0.78f
                                    } else {
                                        when (panel) {
                                            PlayerPanel.AUDIO -> 0.45f
                                            PlayerPanel.EPISODES -> 0.34f
                                            PlayerPanel.STYLE, PlayerPanel.DELAY -> 0.26f
                                            else -> 0.20f
                                        }
                                    }
                                    size.width * (1f - panelFraction)
                                }
                                val insidePanel = if (panel == PlayerPanel.SETTINGS) {
                                    offset.x >= panelStart && offset.y in settingsPanelTopPx..settingsPanelBottomPx
                                } else {
                                    offset.x >= panelStart
                                }
                                if (!insidePanel) {
                                    panel = null
                                    showControls = true
                                }
                            } else {
                                showControls = !showControls
                            }
                        }
                    },
                    onDoubleTap = { offset: Offset ->
                        if (!locked) {
                            if (offset.x < size.width / 2f) {
                                delayedCueEvents.clear()
                                subtitleText = ""
                                player.seekTo((position - 10_000).coerceAtLeast(0L))
                                seekRipple = SeekRipple.LEFT
                            } else {
                                delayedCueEvents.clear()
                                subtitleText = ""
                                player.seekTo(position + 10_000)
                                seekRipple = SeekRipple.RIGHT
                            }
                            showControls = true
                        }
                    },
                )
            },
    ) {
        // Drag layer: left half brightness, right half volume (spec 6.7).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    var side = DragSide.LEFT
                    detectDragGestures(
                        onDragStart = { offset ->
                            side = if (offset.x < size.width / 2f) DragSide.LEFT else DragSide.RIGHT
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (!locked) {
                                gestureHint = when (side) {
                                    DragSide.LEFT -> adjustBrightness(activity, -dragAmount.y / 1200f)
                                    DragSide.RIGHT -> adjustVolume(
                                        audioManager,
                                        -dragAmount.y / 1200f,
                                        volumeLabel,
                                    )
                                }
                            }
                        },
                    )
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        resizeMode = if (fitToScreen) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                        subtitleView?.apply {
                            setUserDefaultStyle()
                            setFractionalTextSize(subtitleTextSize)
                            visibility = View.GONE
                        }
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = if (fitToScreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    view.subtitleView?.apply {
                        setUserDefaultStyle()
                        setFractionalTextSize(subtitleTextSize)
                        visibility = View.GONE
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (subtitleText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isFullscreen) 76.dp else 20.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = subtitleText,
                    color = Color.White,
                    fontSize = subtitleFontSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 42.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        seekRipple?.let { ripple ->
            Box(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                contentAlignment = if (ripple == SeekRipple.LEFT) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                    Text(
                        text = stringRes(R.string.player_seek_badge),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (isBuffering || state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FmHubLoader(Modifier.size(40.dp))
            }
        }

        gestureHint?.let { (label, percent) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 46.dp),
                contentAlignment = if (label == brightnessLabel) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Column(
                    modifier = Modifier
                        .width(54.dp)
                        .height(200.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(27.dp))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((180 * percent.coerceIn(0f, 1f)).dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.White, RoundedCornerShape(13.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${(percent.coerceIn(0f, 1f) * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (!locked && !state.loading && (showControls || !isPlaying || panel != null)) {
            ReferenceControlsOverlay(
                state = state,
                position = position,
                duration = duration,
                isPlaying = isPlaying,
                isFullscreen = isFullscreen,
                speed = speed,
                panel = panel,
                autoMiniplayer = autoMiniplayer,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                audioLabel = audioLabel,
                fitToScreen = fitToScreen,
                subtitleSize = savedSubtitleSize,
                subtitleDelayMs = subtitleDelayMs,
                bilingualSubtitles = bilingualSubtitles,
                subtitleDownloadStatus = subtitleDownloadStatus,
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen,
                onTogglePlay = {
                    if (player.isPlaying) player.pause() else player.play()
                    showControls = true
                },
                onSeekTo = { seconds ->
                    delayedCueEvents.clear()
                    subtitleText = ""
                    player.seekTo(seconds * 1000L)
                },
                onPanel = { chosen ->
                    panel = if (panel == chosen) null else chosen
                    showControls = true
                },
                onToggleFit = { fitToScreen = !fitToScreen },
                onAutoMiniplayerChange = viewModel::setAutoMiniplayer,
                onSubtitleSize = viewModel::saveSubtitleSize,
                onSubtitleDelay = viewModel::saveSubtitleDelay,
                onBilingualSubtitles = { enabled ->
                    val bilingualTrack = subtitleTracks.firstOrNull {
                        it.label?.contains("bilingual", ignoreCase = true) == true
                    }
                    if (bilingualTrack != null) {
                        if (enabled) {
                            val group = tracks.groups[bilingualTrack.groupIndex]
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .addOverride(
                                    androidx.media3.common.TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        bilingualTrack.trackIndex,
                                    ),
                                )
                                .build()
                        } else {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .build()
                        }
                        viewModel.setBilingualSubtitles(enabled)
                    }
                },
                onSubtitleDownload = viewModel::downloadSubtitle,
                onSelectAudioTrack = { choice ->
                    val group = tracks.groups[choice.groupIndex]
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .addOverride(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup,
                                choice.trackIndex,
                            ),
                        )
                        .build()
                },
                onSelectSourceLanguage = { language ->
                    viewModel.switchLanguage(language, position, duration)
                },
                onSpeed = { value ->
                    speed = value
                    viewModel.savePlaybackSpeed(value)
                },
                onSubtitleOff = {
                    delayedCueEvents.clear()
                    subtitleText = ""
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                },
                onSubtitleSelect = { groupIndex, trackIndex ->
                    delayedCueEvents.clear()
                    subtitleText = ""
                    val group = tracks.groups[groupIndex]
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(
                            androidx.media3.common.TrackSelectionOverride(
                                group.mediaTrackGroup,
                                trackIndex,
                            ),
                        )
                        .build()
                },
                onServer = { server ->
                    viewModel.switchSource(server, position, duration)
                },
                onLock = {
                    locked = true
                    showControls = false
                },
                onPip = { enterPip(activity) },
                onShare = {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, viewModel.shareText())
                    }
                    context.startActivity(android.content.Intent.createChooser(share, null))
                },
                onNextEpisode = {
                    state.nextEpisode?.let { next ->
                        viewModel.recordProgress(position, duration)
                        onOpenEpisode(state.item?.id.orEmpty(), next.key, isFullscreen)
                    }
                },
                onSelectEpisode = { selected ->
                    viewModel.recordProgress(position, duration)
                    onOpenEpisode(state.item?.id.orEmpty(), selected.key, isFullscreen)
                },
                onReport = { reportOpen = true },
            )
        }

        if (locked) {
            LockedOverlay(onUnlock = {
                locked = false
                showControls = true
            })
        }

        if (!resumeAsked && state.resumePositionMs > 5_000L && duration > 0L) {
            ResumeDialog(
                positionMs = state.resumePositionMs,
                onResume = {
                    startAt = state.resumePositionMs
                    resumeAsked = true
                },
                onStartOver = {
                    startAt = 1L
                    resumeAsked = true
                },
            )
        }

        if (reportOpen) {
            ReportSheet(
                onDismiss = { reportOpen = false },
                onSend = { reason ->
                    viewModel.sendReport(reason)
                    reportOpen = false
                },
            )
        }
    }
    if (!isFullscreen) {
        PortraitPlayerInfo(
            state = state,
            audioLabel = audioLabel,
            onAudio = {
                panel = PlayerPanel.AUDIO
                showControls = true
            },
            onFullscreen = onToggleFullscreen,
        )
    }
    if (!isFullscreen && state.isSeries) {
        EpisodePlaylist(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onSelect = { selected ->
                viewModel.recordProgress(position, duration)
                onOpenEpisode(state.item?.id.orEmpty(), selected.key, false)
            },
        )
    }
    }
}

@Composable
private fun PortraitPlayerInfo(
    state: PlayerUiState,
    audioLabel: String,
    onAudio: () -> Unit,
    onFullscreen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF08130F))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = state.title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(state.subtitle, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
        }
        val meta = listOfNotNull(
            state.item?.releaseYear?.toString(),
            state.item?.rating?.takeIf { it > 0.0 }?.let { "★ ${"%.1f".format(it)}" },
        )
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(meta.joinToString("  ·  "), color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayerActionButton(
                icon = Icons.Outlined.Audiotrack,
                label = stringRes(R.string.chip_language),
                value = audioLabel,
                active = false,
                onClick = onAudio,
            )
            PlayerActionButton(
                icon = Icons.Outlined.Fullscreen,
                label = stringRes(R.string.player_fullscreen),
                value = null,
                active = false,
                onClick = onFullscreen,
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    state: PlayerUiState,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    speed: Float,
    panel: PlayerPanel?,
    audioTracks: List<TrackChoice>,
    subtitleTracks: List<TrackChoice>,
    audioLabel: String,
    fitToScreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onToggleFit: () -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectAudioTrack: (TrackChoice) -> Unit,
    onSelectSourceLanguage: (String) -> Unit,
    onSubtitleOff: () -> Unit,
    onSubtitleSelect: (Int, Int) -> Unit,
    onServer: (MediaSource) -> Unit,
    onLock: () -> Unit,
    onPip: () -> Unit,
    onShare: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectEpisode: (EpisodeItem) -> Unit,
    onReport: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                    ),
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.subtitle.isNotEmpty()) {
                    Text(
                        text = state.subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }
            }
            if (isFullscreen) {
                TextButton(onClick = onLock) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text(stringRes(R.string.player_lock_prompt), color = Color.White, fontSize = 11.sp)
                }
            }
            if (state.isSeries && state.nextEpisode != null) {
                IconButton(onClick = onNextEpisode) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = stringRes(R.string.player_next_episode),
                        tint = Color.White,
                    )
                }
            }
            if (isFullscreen) {
                IconButton(onClick = onReport) {
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = stringRes(R.string.report_problem),
                        tint = Color.White,
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White)
            }
            IconButton(onClick = onPip) {
                Icon(
                    Icons.Outlined.PictureInPictureAlt,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = stringRes(
                        if (isFullscreen) R.string.player_exit_fullscreen
                        else R.string.player_fullscreen,
                    ),
                    tint = Color.White,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSeekTo((position - 10_000).coerceAtLeast(0L) / 1000) }) {
                Icon(
                    Icons.Outlined.Replay10,
                    contentDescription = stringRes(R.string.player_back_10),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringRes(
                        if (isPlaying) R.string.action_pause else R.string.action_play,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            IconButton(onClick = { onSeekTo((position + 10_000) / 1000) }) {
                Icon(
                    Icons.Outlined.Forward10,
                    contentDescription = stringRes(R.string.player_forward_10),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatClock(position), color = Color.White, fontSize = 11.sp)
                val maxSeconds = if (duration > 0L) duration / 1000 else 1L
                Slider(
                    value = (position / 1000f).coerceIn(0f, maxSeconds.toFloat()),
                    onValueChange = { value -> onSeekTo(value.toLong()) },
                    valueRange = 0f..maxSeconds.toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                Text(
                    text = if (duration > 0L) formatClock(duration) else "LIVE",
                    color = Color.White,
                    fontSize = 11.sp,
                )
            }

            if (isFullscreen) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                PlayerActionButton(
                    icon = Icons.Outlined.AspectRatio,
                    label = stringRes(if (fitToScreen) R.string.player_fit else R.string.player_fill),
                    value = null,
                    active = false,
                    onClick = onToggleFit,
                )
                PlayerActionButton(
                    icon = Icons.Outlined.Audiotrack,
                    label = stringRes(R.string.chip_language),
                    value = audioLabel,
                    active = panel == PlayerPanel.AUDIO,
                    onClick = { onPanel(PlayerPanel.AUDIO) },
                )
                PlayerActionButton(
                    icon = Icons.Outlined.Speed,
                    label = stringRes(R.string.chip_speed),
                    value = "${speed}x",
                    active = panel == PlayerPanel.SPEED,
                    onClick = { onPanel(PlayerPanel.SPEED) },
                )
                val quality = state.source?.quality?.let { "${it}p" }
                    ?: stringRes(R.string.quality_auto)
                PlayerActionButton(
                    icon = Icons.Outlined.HighQuality,
                    label = stringRes(R.string.chip_quality),
                    value = quality,
                    active = panel == PlayerPanel.QUALITY,
                    onClick = { onPanel(PlayerPanel.QUALITY) },
                )
                if (state.isSeries) {
                    PlayerActionButton(
                        icon = Icons.Outlined.SkipNext,
                        label = stringRes(R.string.chip_episodes),
                        value = state.subtitle,
                        active = panel == PlayerPanel.EPISODES,
                        onClick = { onPanel(PlayerPanel.EPISODES) },
                    )
                }
                    PlayerActionButton(
                        icon = Icons.Outlined.Settings,
                        label = stringRes(R.string.player_settings),
                        value = null,
                        active = panel == PlayerPanel.SERVERS,
                        onClick = { onPanel(PlayerPanel.SERVERS) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = panel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
                modifier = Modifier.width(300.dp),
            ) {
                when (panel) {
                    PlayerPanel.SPEED -> SpeedPanel(current = speed, onSelect = onSpeed)
                    PlayerPanel.AUDIO -> AudioPanel(
                        state = state,
                        audioTracks = audioTracks,
                        subtitleTracks = subtitleTracks,
                        onSelectAudioTrack = onSelectAudioTrack,
                        onSelectSourceLanguage = onSelectSourceLanguage,
                        onSubtitleOff = onSubtitleOff,
                        onSubtitleSelect = onSubtitleSelect,
                    )
                    PlayerPanel.SERVERS -> ServerPanel(state = state, onServer = onServer)
                    PlayerPanel.QUALITY -> QualityPanel(state = state, onServer = onServer)
                    else -> EpisodePanel(state = state, onSelectEpisode = onSelectEpisode)
                }
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (active) Color(0xFFF4B84A).copy(alpha = 0.25f)
        else Color.Black.copy(alpha = 0.46f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) Color(0xFFF4B84A) else Color.White,
                modifier = Modifier.size(17.dp),
            )
            Column {
                Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
                if (value != null) {
                    Text(
                        value,
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioPanel(
    state: PlayerUiState,
    audioTracks: List<TrackChoice>,
    subtitleTracks: List<TrackChoice>,
    onSelectAudioTrack: (TrackChoice) -> Unit,
    onSelectSourceLanguage: (String) -> Unit,
    onSubtitleOff: () -> Unit,
    onSubtitleSelect: (Int, Int) -> Unit,
) {
    val sourceLanguages = state.languages.filter { language ->
        audioTracks.none { track ->
            track.label?.equals(language, ignoreCase = true) == true
        }
    }
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Text(stringRes(R.string.audio_label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(6.dp))
        if (audioTracks.isEmpty() && sourceLanguages.isEmpty()) {
            Text(
                stringRes(R.string.player_no_audio_tracks),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.58f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        audioTracks.forEach { track ->
            PanelRow(
                label = track.label(R.string.player_audio_track),
                trailing = null,
                selected = track.selected,
                onClick = { onSelectAudioTrack(track) },
            )
        }
        if (sourceLanguages.isNotEmpty()) {
            if (audioTracks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringRes(R.string.player_audio_streams),
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.58f),
                )
            }
            sourceLanguages.forEach { language ->
                val source = state.alternatives.firstOrNull {
                    SourceSelection.keyOf(it).equals(language, ignoreCase = true)
                }
                PanelRow(
                    label = SourceSelection.languageLabel(language, source?.isDub == true),
                    trailing = null,
                    selected = language.equals(state.languageKey, ignoreCase = true),
                    onClick = { onSelectSourceLanguage(language) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringRes(R.string.player_no_subtitles),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        PanelRow(
            label = stringRes(R.string.subtitles_off),
            trailing = null,
            selected = subtitleTracks.none { it.selected },
            onClick = onSubtitleOff,
        )
        subtitleTracks.forEach { track ->
            PanelRow(
                label = track.label(R.string.player_subtitle_track),
                trailing = null,
                selected = track.selected,
                onClick = { onSubtitleSelect(track.groupIndex, track.trackIndex) },
            )
        }
    }
}

@Composable
private fun ServerPanel(state: PlayerUiState, onServer: (MediaSource) -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Text(stringRes(R.string.sheet_servers), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        if (state.alternatives.isEmpty()) {
            Text(
                stringRes(R.string.player_no_alternative_servers),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
        state.alternatives.take(8).forEach { server ->
            val supported = CodecSupport.isSupported(server.codec)
            PanelRow(
                label = listOfNotNull(
                    server.serverLabel,
                    SourceSelection.qualityLabel(server.quality),
                    server.codecLabel,
                ).joinToString(" · "),
                trailing = if (supported) null else stringRes(R.string.unsupported_on_phone),
                selected = server.mediaUrl == state.source?.mediaUrl,
                supported = supported,
                onClick = { onServer(server) },
            )
        }
    }
}

@Composable
private fun QualityPanel(state: PlayerUiState, onServer: (MediaSource) -> Unit) {
    val qualities = state.alternatives.groupBy { SourceSelection.qualityLabel(it.quality) }
    Column(
        modifier = Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Text(stringRes(R.string.chip_quality), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        if (qualities.isEmpty()) {
            Text(
                stringRes(R.string.player_no_alternative_servers),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
        qualities.forEach { (quality, servers) ->
            val supportedServers = servers.filter { CodecSupport.isSupported(it.codec) }
            val target = supportedServers.firstOrNull() ?: servers.first()
            PanelRow(
                label = quality,
                trailing = null,
                selected = servers.any { it.mediaUrl == state.source?.mediaUrl },
                supported = supportedServers.isNotEmpty(),
                onClick = { onServer(target) },
            )
        }
    }
}

/** Design `.r` row inside the player settings panel. */
@Composable
private fun PanelRow(
    label: String,
    trailing: String?,
    selected: Boolean,
    onClick: () -> Unit,
    supported: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected && supported) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    Color.White.copy(alpha = 0.06f)
                },
            )
            .border(
                width = 1.dp,
                color = if (selected && supported) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.12f)
                },
                shape = RoundedCornerShape(10.dp),
            )
            .alpha(if (supported) 1f else 0.45f)
            .clickable(enabled = supported, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected && supported) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun SpeedPanel(current: Float, onSelect: (Float) -> Unit) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringRes(R.string.chip_speed),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                SelectChip(
                    text = "${value}x",
                    selected = current == value,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}

@Composable
private fun EpisodePlaylist(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onSelect: (EpisodeItem) -> Unit,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF08130F))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringRes(R.string.player_episode_playlist),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(state.episodes, key = { it.key }) { episode ->
                EpisodeQueueItem(
                    episode = episode,
                    current = episode.key == state.episode?.key,
                    onClick = { onSelect(episode) },
                )
            }
        }
    }
}

@Composable
private fun EpisodePanel(
    state: PlayerUiState,
    onSelectEpisode: (EpisodeItem) -> Unit,
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = stringRes(R.string.player_episode_playlist),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            items(state.episodes, key = { it.key }) { episode ->
                EpisodeQueueItem(
                    episode = episode,
                    current = episode.key == state.episode?.key,
                    onClick = { onSelectEpisode(episode) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeQueueItem(
    episode: EpisodeItem,
    current: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${episode.episodeNumber}",
            color = if (current) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title?.takeIf { it.isNotBlank() }
                    ?: "Episode ${episode.episodeNumber}",
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "S${episode.seasonNumber} · E${episode.episodeNumber}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
        }
        if (current) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun LockedOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(start = 16.dp)
                .clickable(onClick = onUnlock),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringRes(R.string.player_unlock),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(88.dp),
                )
            }
        }
    }
}

@Composable
private fun ResumeDialog(positionMs: Long, onResume: () -> Unit, onStartOver: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStartOver,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.resume_title)) },
        text = { Text(stringRes(R.string.resume_at, formatClock(positionMs))) },
        confirmButton = {
            TextButton(onClick = onResume) { Text(stringRes(R.string.action_play)) }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) { Text(stringRes(R.string.start_over)) }
        },
    )
}

@Composable
private fun FailureOverlay(
    failure: StreamError,
    state: PlayerUiState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val reportLabel = stringRes(R.string.report_problem)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        FailureContent(
            background = MaterialTheme.colorScheme.background,
            message = when (failure) {
                StreamError.EXPIRED -> stringRes(R.string.link_expired)
                StreamError.OFFLINE -> stringRes(R.string.state_network_error)
                StreamError.FAILED -> stringRes(R.string.playback_failed)
            },
            alternatives = state.alternatives,
            onTry = { source ->
                viewModel.switchSource(
                    source,
                    state.resumePositionMs,
                    state.resumeDurationMs,
                )
            },
            onReport = { viewModel.sendReport(reportLabel) },
            reportLabel = reportLabel,
            onBack = onBack,
        )
    }
}

@Composable
private fun FailureContent(
    background: Color,
    message: String,
    alternatives: List<MediaSource>,
    onTry: ((MediaSource) -> Unit)?,
    onReport: (() -> Unit)?,
    onBack: () -> Unit,
    reportLabel: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        if (alternatives.isNotEmpty() && onTry != null) {
            Text(
                text = stringRes(R.string.other_servers),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alternatives) { server ->
                    Surface(
                        onClick = { onTry(server) },
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "${server.serverLabel} · " +
                                SourceSelection.qualityLabel(server.quality),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { onTry(alternatives.first()) }) {
                Text(
                    text = stringRes(R.string.try_now),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
        }

        if (onReport != null) {
            TextButton(onClick = onReport) {
                Text(
                    text = reportLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack) {
            Text(
                text = stringRes(R.string.action_back),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ReportSheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val reasons = listOf(
        stringRes(R.string.report_reason_broken),
        stringRes(R.string.report_reason_language),
        stringRes(R.string.report_reason_audio),
        stringRes(R.string.report_reason_quality),
        stringRes(R.string.report_reason_other),
    )
    var selected by remember { mutableStateOf(reasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.report_title)) },
        text = {
            Column {
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selected == reason,
                            onClick = { selected = reason },
                        )
                        Text(reason, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(selected) }) {
                Text(stringRes(R.string.report_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ReportSentMessage(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.report_problem)) },
        text = { Text(stringRes(R.string.report_not_connected)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.action_ok)) }
        },
    )
}

// -------------------------------------------------------------------------------------------
// Platform helpers
// -------------------------------------------------------------------------------------------

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun adjustBrightness(activity: Activity?, delta: Float): Pair<String, Float>? {
    activity ?: return null
    val window = activity.window
    val current = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f
    val next = (current + delta).coerceIn(0.02f, 1f)
    val attributes = window.attributes
    attributes.screenBrightness = next
    window.attributes = attributes
    return activity.getString(R.string.player_brightness) to next
}

private fun adjustVolume(
    audioManager: AudioManager,
    delta: Float,
    label: String,
): Pair<String, Float>? {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val target = if (delta > 0f) current + 1 else current - 1
    val clamped = target.coerceIn(0, max)
    if (clamped != current) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    }
    val value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return label to (value.toFloat() / max).coerceIn(0f, 1f)
}

private fun enterPip(activity: Activity?) {
    activity ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching { activity.enterPictureInPictureMode() }
    }
}


@Composable
private fun ReferenceControlsOverlay(
    state: PlayerUiState,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    speed: Float,
    panel: PlayerPanel?,
    autoMiniplayer: Boolean,
    audioTracks: List<TrackChoice>,
    subtitleTracks: List<TrackChoice>,
    audioLabel: String,
    fitToScreen: Boolean,
    subtitleSize: String,
    subtitleDelayMs: Int,
    bilingualSubtitles: Boolean,
    subtitleDownloadStatus: SubtitleDownloadStatus,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onToggleFit: () -> Unit,
    onAutoMiniplayerChange: (Boolean) -> Unit,
    onSubtitleSize: (String) -> Unit,
    onSubtitleDelay: (Int) -> Unit,
    onBilingualSubtitles: (Boolean) -> Unit,
    onSubtitleDownload: (String?) -> Unit,
    onSpeed: (Float) -> Unit,
    onSelectAudioTrack: (TrackChoice) -> Unit,
    onSelectSourceLanguage: (String) -> Unit,
    onSubtitleOff: () -> Unit,
    onSubtitleSelect: (Int, Int) -> Unit,
    onServer: (MediaSource) -> Unit,
    onLock: () -> Unit,
    onReport: () -> Unit,
    onPip: () -> Unit,
    onShare: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectEpisode: (EpisodeItem) -> Unit,
) {
    if (!isFullscreen) {
        ControlsOverlay(
            state = state,
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            isFullscreen = false,
            speed = speed,
            panel = panel,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            audioLabel = audioLabel,
            fitToScreen = fitToScreen,
            onBack = onBack,
            onToggleFullscreen = onToggleFullscreen,
            onTogglePlay = onTogglePlay,
            onSeekTo = onSeekTo,
            onPanel = onPanel,
            onToggleFit = onToggleFit,
            onSpeed = onSpeed,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectSourceLanguage = onSelectSourceLanguage,
            onSubtitleOff = onSubtitleOff,
            onSubtitleSelect = onSubtitleSelect,
            onServer = onServer,
            onLock = onLock,
            onPip = onPip,
            onShare = onShare,
            onNextEpisode = onNextEpisode,
            onSelectEpisode = onSelectEpisode,
            onReport = onReport,
        )
    } else {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                    ),
                )
                .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.subtitle.isNotEmpty()) {
                        Text(
                            text = state.subtitle,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
                if (isFullscreen) {
                    IconButton(onClick = onReport) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = stringRes(R.string.report_problem),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { onPanel(PlayerPanel.SETTINGS) }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringRes(R.string.player_settings),
                            tint = if (panel == PlayerPanel.SETTINGS) PLAYER_GREEN else Color.White,
                        )
                    }
                } else {
                    IconButton(onClick = onToggleFullscreen) {
                        Icon(
                            Icons.Outlined.Fullscreen,
                            contentDescription = stringRes(R.string.player_fullscreen),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (isFullscreen) {
            Surface(
                onClick = onLock,
                color = Color.Black.copy(alpha = 0.38f),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(stringRes(R.string.player_lock_prompt), color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = if (isFullscreen) 58.dp else 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSeekTo((position - 10_000).coerceAtLeast(0L) / 1000) }) {
                Icon(Icons.Outlined.Replay10, contentDescription = stringRes(R.string.player_back_10), tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringRes(if (isPlaying) R.string.action_pause else R.string.action_play),
                    tint = Color.White,
                    modifier = Modifier.size(50.dp),
                )
            }
            IconButton(onClick = { onSeekTo((position + 10_000) / 1000) }) {
                Icon(Icons.Outlined.Forward10, contentDescription = stringRes(R.string.player_forward_10), tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                    ),
                )
                .padding(horizontal = if (isFullscreen) 34.dp else 14.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(position), color = Color.White, fontSize = 11.sp)
                val maxSeconds = if (duration > 0L) duration / 1000L else 1L
                Slider(
                    value = (position / 1000f).coerceIn(0f, maxSeconds.toFloat()),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..maxSeconds.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = PLAYER_GREEN,
                        activeTrackColor = PLAYER_GREEN,
                        inactiveTrackColor = Color.White.copy(alpha = 0.36f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Text(if (duration > 0L) formatClock(duration) else "LIVE", color = Color.White, fontSize = 11.sp)
            }
            if (isFullscreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(38.dp)) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = stringRes(if (isPlaying) R.string.action_pause else R.string.action_play),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    LandscapeActionIcon(
                        icon = Icons.Outlined.AspectRatio,
                        description = stringRes(if (fitToScreen) R.string.player_fit else R.string.player_fill),
                        selected = false,
                        onClick = onToggleFit,
                    )
                    LandscapeActionIcon(
                        icon = Icons.Outlined.Audiotrack,
                        description = stringRes(R.string.chip_language),
                        selected = panel == PlayerPanel.AUDIO,
                        onClick = { onPanel(PlayerPanel.AUDIO) },
                    )
                    if (panel == PlayerPanel.AUDIO || panel == PlayerPanel.STYLE || panel == PlayerPanel.DELAY) {
                        LandscapeActionIcon(
                            icon = Icons.Outlined.TextFields,
                            description = stringRes(R.string.player_subtitle_style),
                            selected = panel == PlayerPanel.STYLE,
                            onClick = { onPanel(PlayerPanel.STYLE) },
                        )
                        LandscapeActionIcon(
                            icon = Icons.Outlined.Timer,
                            description = stringRes(R.string.player_subtitle_delay),
                            selected = panel == PlayerPanel.DELAY,
                            onClick = { onPanel(PlayerPanel.DELAY) },
                        )
                    }
                    LandscapeActionIcon(
                        icon = Icons.Outlined.Speed,
                        description = stringRes(R.string.chip_speed),
                        value = stringRes(R.string.player_speed_value, speed.toString().trimEnd('0').trimEnd('.')),
                        selected = panel == PlayerPanel.SPEED,
                        onClick = { onPanel(PlayerPanel.SPEED) },
                    )
                    val quality = state.source?.quality?.let { "${it}p" } ?: stringRes(R.string.quality_auto)
                    LandscapeActionIcon(
                        icon = Icons.Outlined.HighQuality,
                        description = stringRes(R.string.chip_quality),
                        value = quality,
                        selected = panel == PlayerPanel.QUALITY,
                        onClick = { onPanel(PlayerPanel.QUALITY) },
                    )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = panel == PlayerPanel.SETTINGS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 16.dp),
        ) {
            Surface(
                color = Color(0xF0222524),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.width(320.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringRes(R.string.player_auto_miniplayer_title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(stringRes(R.string.player_auto_miniplayer_description), color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, lineHeight = 14.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = autoMiniplayer,
                        onCheckedChange = onAutoMiniplayerChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PLAYER_GREEN,
                            checkedBorderColor = PLAYER_GREEN,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF55595B),
                            uncheckedBorderColor = Color(0xFF55595B),
                        ),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = panel != null && panel != PlayerPanel.SETTINGS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(
                    when (panel) {
                        PlayerPanel.AUDIO -> 0.45f
                        PlayerPanel.EPISODES -> 0.34f
                        PlayerPanel.STYLE, PlayerPanel.DELAY -> 0.26f
                        else -> 0.20f
                    },
                ),
        ) {
            Surface(
                color = Color(0xD90B100F),
                shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (panel) {
                    PlayerPanel.AUDIO -> LandscapeAudioPanel(
                        state = state,
                        audioTracks = audioTracks,
                        subtitleTracks = subtitleTracks,
                        bilingual = bilingualSubtitles,
                        downloadStatus = subtitleDownloadStatus,
                        onSelectAudioTrack = onSelectAudioTrack,
                        onSelectSourceLanguage = onSelectSourceLanguage,
                        onSubtitleOff = onSubtitleOff,
                        onSubtitleSelect = onSubtitleSelect,
                        onBilingual = onBilingualSubtitles,
                        onDownload = onSubtitleDownload,
                    )
                    PlayerPanel.SPEED -> LandscapeSpeedPanel(speed, onSpeed)
                    PlayerPanel.QUALITY, PlayerPanel.SERVERS -> LandscapeQualityPanel(state, onServer)
                    PlayerPanel.STYLE -> LandscapeStylePanel(subtitleSize, onSubtitleSize)
                    PlayerPanel.DELAY -> LandscapeDelayPanel(subtitleDelayMs, onSubtitleDelay)
                    PlayerPanel.EPISODES -> LandscapeEpisodePanel(state, onSelectEpisode)
                    else -> Unit
                }
            }
        }
    }
    }
}

private val PLAYER_GREEN = Color(0xFF25D08B)

@Composable
private fun LandscapeActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    value: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = description, tint = if (selected) PLAYER_GREEN else Color.White, modifier = Modifier.size(20.dp))
        value?.let { Text(it, color = if (selected) PLAYER_GREEN else Color.White, fontSize = 12.sp, maxLines = 1) }
    }
}

@Composable
private fun LandscapeAudioPanel(
    state: PlayerUiState,
    audioTracks: List<TrackChoice>,
    subtitleTracks: List<TrackChoice>,
    bilingual: Boolean,
    downloadStatus: SubtitleDownloadStatus,
    onSelectAudioTrack: (TrackChoice) -> Unit,
    onSelectSourceLanguage: (String) -> Unit,
    onSubtitleOff: () -> Unit,
    onSubtitleSelect: (Int, Int) -> Unit,
    onBilingual: (Boolean) -> Unit,
    onDownload: (String?) -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text(stringRes(R.string.audio_tracks), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            audioTracks.forEach { track ->
                LandscapeOptionRow(track.label(R.string.player_audio_track), track.selected) { onSelectAudioTrack(track) }
            }
            state.languages.distinct().forEach { language ->
                val label = SourceSelection.languageLabel(language, false)
                val selected = language.equals(state.languageKey, ignoreCase = true)
                LandscapeOptionRow(label, selected) { onSelectSourceLanguage(language) }
            }
            if (audioTracks.isEmpty() && state.languages.isEmpty()) {
                Text(stringRes(R.string.player_no_audio_tracks), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.16f)))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text(stringRes(R.string.player_no_subtitles), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            val bilingualTrack = subtitleTracks.firstOrNull { it.label?.contains("bilingual", ignoreCase = true) == true }
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(R.string.player_bilingual), color = if (bilingualTrack != null) Color.White else Color.White.copy(alpha = 0.45f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = bilingual && bilingualTrack != null,
                    enabled = bilingualTrack != null,
                    onCheckedChange = onBilingual,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PLAYER_GREEN,
                        checkedBorderColor = PLAYER_GREEN,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF55595B),
                        uncheckedBorderColor = Color(0xFF55595B),
                    ),
                )
            }
            if (bilingualTrack == null) {
                Text(stringRes(R.string.player_bilingual_unavailable), color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
            LandscapeOptionRow(stringRes(R.string.subtitles_off), subtitleTracks.none { it.selected }, onSubtitleOff)
            subtitleTracks.forEach { track ->
                LandscapeOptionRow(track.label(R.string.player_subtitle_track), track.selected) {
                    onSubtitleSelect(track.groupIndex, track.trackIndex)
                }
            }
            val selectedTrack = subtitleTracks.firstOrNull { it.selected }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectedTrack?.languageCode != null) { onDownload(selectedTrack?.languageCode) }
                    .padding(vertical = 13.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.ClosedCaption, contentDescription = null, tint = if (selectedTrack?.languageCode != null) PLAYER_GREEN else Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text(stringRes(R.string.player_subtitle_download), color = if (selectedTrack?.languageCode != null) Color.White else Color.White.copy(alpha = 0.42f), fontSize = 12.sp)
            }
            val statusText = when (downloadStatus) {
                SubtitleDownloadStatus.IDLE -> null
                SubtitleDownloadStatus.DOWNLOADING -> stringRes(R.string.player_subtitle_download_running)
                SubtitleDownloadStatus.STARTED -> stringRes(R.string.player_subtitle_download_started)
                SubtitleDownloadStatus.UNAVAILABLE -> stringRes(R.string.player_subtitle_download_failed)
                SubtitleDownloadStatus.FAILED -> stringRes(R.string.player_subtitle_download_error)
            }
            statusText?.let { Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
        }
    }
}

@Composable
private fun LandscapeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) PLAYER_GREEN else Color.White.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = PLAYER_GREEN, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun LandscapeSpeedPanel(current: Float, onSelect: (Float) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 15.dp), verticalArrangement = Arrangement.Center) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
            LandscapeOptionRow("${speed}×", current == speed) { onSelect(speed) }
        }
    }
}

@Composable
private fun LandscapeQualityPanel(state: PlayerUiState, onServer: (MediaSource) -> Unit) {
    val sources = (listOfNotNull(state.source) + state.alternatives).distinctBy { it.mediaUrl }
        .sortedByDescending { it.quality ?: 0 }
        .distinctBy { it.quality }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 15.dp), verticalArrangement = Arrangement.Center) {
        sources.forEach { source ->
            val label = source.quality?.let { "${it}p" } ?: stringRes(R.string.quality_auto)
            LandscapeOptionRow(label, source.mediaUrl == state.source?.mediaUrl) { onServer(source) }
        }
        if (sources.isEmpty()) Text(stringRes(R.string.quality_auto), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

@Composable
private fun LandscapeStylePanel(current: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
        Text(stringRes(R.string.player_subtitle_style), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 9.dp))
        listOf("small" to R.string.text_size_small, "medium" to R.string.text_size_medium, "large" to R.string.text_size_large).forEach { (size, label) ->
            LandscapeOptionRow(stringRes(label), current == size) { onSelect(size) }
        }
    }
}

@Composable
private fun LandscapeDelayPanel(current: Int, onSelect: (Int) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
        Text(stringRes(R.string.player_subtitle_delay), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(stringRes(R.string.player_subtitle_delay_value, "%.1f".format(Locale.ROOT, value / 1000f)), color = PLAYER_GREEN, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = 0f..5_000f,
            steps = 9,
            onValueChangeFinished = { onSelect(value.toInt()) },
            colors = SliderDefaults.colors(
                thumbColor = PLAYER_GREEN,
                activeTrackColor = PLAYER_GREEN,
                inactiveTrackColor = Color.White.copy(alpha = 0.36f),
            ),
        )
        Text(stringRes(R.string.player_subtitle_delay_later), color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
    }
}

@Composable
private fun LandscapeEpisodePanel(state: PlayerUiState, onSelect: (EpisodeItem) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        state.episodes.forEach { episode ->
            val label = episode.title.orEmpty().ifBlank {
                "S${episode.seasonNumber} E${episode.episodeNumber}"
            }
            LandscapeOptionRow(label, episode.key == state.episode?.key) { onSelect(episode) }
        }
    }
}
