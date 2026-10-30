@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.fmhub24.android.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fmhub24.android.R
import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.ui.FmHubLoader
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.formatClock
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.stream.StreamEngine
import com.fmhub24.android.stream.StreamError
import kotlinx.coroutines.delay

private enum class SeekRipple { LEFT, RIGHT }
private enum class PlayerPanel { SETTINGS, EPISODES, SPEED, SUBTITLES }
private enum class DragSide { LEFT, RIGHT }

/** Landscape player (spec 6.7): controls, gestures, panels, resume, failure and next episode. */
@Composable
fun PlayerScreen(
    contentId: String,
    episodeKey: String?,
    onBack: () -> Unit,
    onOpenEpisode: (String, String?) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val activity = LocalContext.current.findActivity()

    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(onBack = onBack)

    val background = Color(0xFF05052B)

    when {
        state.loading -> Box(
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
    onBack: () -> Unit,
    onOpenEpisode: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    val source = state.source ?: return
    val headers = source.headers

    val player = remember(source.mediaUrl) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(StreamEngine.dataSourceFactory(headers)),
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaItem(
                    androidx.media3.common.MediaItem.Builder()
                        .setUri(source.mediaUrl)
                        .setMimeType(StreamEngine.mimeTypeFor(source))
                        .build(),
                )
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(source.mediaUrl) {
        onDispose { player.release() }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }

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

    LaunchedEffect(player, position) {
        if (duration > 0L && position in 2_000L..duration) {
            viewModel.recordProgress(position, duration)
        }
    }

    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4_000)
            showControls = false
        }
    }

    var resumeAsked by remember { mutableStateOf(state.resumePositionMs < 5_000L) }
    var startAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startAt) {
        if (startAt > 0L) player.seekTo(startAt)
    }

    var locked by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var speed by remember { mutableFloatStateOf(1f) }
    var gestureHint by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var seekRipple by remember { mutableStateOf<SeekRipple?>(null) }
    var castNotice by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }

    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }
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
    val activity = context.findActivity()
    val brightnessLabel = stringRes(R.string.player_brightness)
    val volumeLabel = stringRes(R.string.player_volume)
    val reportLabel = stringRes(R.string.report_problem)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        if (!locked) showControls = !showControls
                    },
                    onDoubleTap = { offset: Offset ->
                        if (!locked) {
                            if (offset.x < size.width / 2f) {
                                player.seekTo((position - 10_000).coerceAtLeast(0L))
                                seekRipple = SeekRipple.LEFT
                            } else {
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
                    SurfaceView(ctx).apply { player.setVideoSurfaceView(this) }
                },
                modifier = Modifier.fillMaxSize(),
            )
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

        if (isBuffering) {
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

        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ControlsOverlay(
                state = state,
                position = position,
                duration = duration,
                isPlaying = isPlaying,
                speed = speed,
                panel = panel,
                tracks = tracks,
                onBack = onBack,
                onTogglePlay = {
                    if (player.isPlaying) player.pause() else player.play()
                    showControls = true
                },
                onSeekTo = { seconds -> player.seekTo(seconds * 1000L) },
                onPanel = { chosen ->
                    panel = if (panel == chosen) null else chosen
                    showControls = true
                },
                onSpeed = { value -> speed = value; panel = null },
                onLanguage = { language ->
                    viewModel.switchLanguage(language)
                    panel = null
                },
                onServer = { server ->
                    viewModel.switchSource(server)
                    panel = null
                },
                onLock = {
                    locked = true
                    showControls = false
                },
                onCast = { castNotice = true },
                onPip = { enterPip(activity) },
                onShare = {},
                onNextEpisode = {
                    state.nextEpisode?.let { next ->
                        viewModel.recordProgress(duration, duration)
                        onOpenEpisode(state.item?.id.orEmpty(), next.key)
                    }
                },
                onReport = { reportOpen = true },
                reportLabel = reportLabel,
            )
        }

        if (locked) {
            LockedOverlay(onUnlock = { locked = false })
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

        if (castNotice) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { castNotice = false },
                contentAlignment = Alignment.Center,
            ) {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = stringRes(R.string.cast_coming_soon),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    )
                }
            }
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
}

@Composable
private fun ControlsOverlay(
    state: PlayerUiState,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    speed: Float,
    panel: PlayerPanel?,
    tracks: Tracks,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onSpeed: (Float) -> Unit,
    onLanguage: (String) -> Unit,
    onServer: (String) -> Unit,
    onLock: () -> Unit,
    onCast: () -> Unit,
    onPip: () -> Unit,
    onShare: () -> Unit,
    onNextEpisode: () -> Unit,
    onReport: () -> Unit,
    reportLabel: String = "",
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
            if (state.isSeries && state.nextEpisode != null) {
                IconButton(onClick = onNextEpisode) {
                    Icon(
                        Icons.Outlined.SkipNext,
                        contentDescription = stringRes(R.string.player_next_episode),
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
            IconButton(onClick = onCast) {
                Icon(Icons.Outlined.Cast, contentDescription = null, tint = Color.White)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isSeries) {
                    PlayerChip(
                        text = stringRes(R.string.chip_episodes),
                        onClick = { onPanel(PlayerPanel.EPISODES) },
                        active = panel == PlayerPanel.EPISODES,
                    )
                }
                PlayerChip(
                    text = stringRes(R.string.chip_language),
                    onClick = { onPanel(PlayerPanel.SETTINGS) },
                    active = panel == PlayerPanel.SETTINGS,
                )
                PlayerChip(
                    text = "${speed}x",
                    onClick = { onPanel(PlayerPanel.SPEED) },
                    active = panel == PlayerPanel.SPEED,
                )
                if (tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }) {
                    PlayerChip(
                        text = stringRes(R.string.player_no_subtitles),
                        onClick = { onPanel(PlayerPanel.SUBTITLES) },
                        active = panel == PlayerPanel.SUBTITLES,
                    )
                }
                Spacer(Modifier.weight(1f))
                PlayerChip(
                    text = stringRes(R.string.chip_lock),
                    onClick = onLock,
                    active = false,
                )
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
                    PlayerPanel.SUBTITLES -> SubtitlesPanel()
                    PlayerPanel.SETTINGS -> LanguageServerPanel(
                        state = state,
                        onLanguage = onLanguage,
                        onServer = onServer,
                        onReport = onReport,
                    )
                    else -> EpisodePanel(state = state, onReport = onReport)
                }
            }
        }
    }
}

@Composable
private fun PlayerChip(text: String, onClick: () -> Unit, active: Boolean) {
    Surface(
        onClick = onClick,
        color = if (active) MaterialTheme.colorScheme.primary
        else Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

/** Three tabs of the settings panel: Language, Server and Quality come from the same list. */
@Composable
private fun LanguageServerPanel(
    state: PlayerUiState,
    onLanguage: (String) -> Unit,
    onServer: (String) -> Unit,
    onReport: () -> Unit,
) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringRes(R.string.sheet_languages),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.languages.take(3).forEach { language ->
                SelectChip(
                    text = SourceSelection.languageLabel(language, false),
                    selected = language == state.languageKey,
                    onClick = { onLanguage(language) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringRes(R.string.sheet_servers),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        state.alternatives.take(8).forEach { server ->
            val supported = CodecSupport.isSupported(server.codec)
            val line = listOfNotNull(
                server.serverLabel,
                SourceSelection.qualityLabel(server.quality),
                server.codecLabel,
            ).joinToString(" · ")
            Text(
                text = if (supported) line
                else "$line · ${stringRes(R.string.unsupported_on_phone)}",
                fontSize = 12.sp,
                color = if (supported) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = supported) { onServer(server.serverLabel) }
                    .padding(vertical = 7.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringRes(R.string.report_problem),
            fontSize = 12.sp,
            color = Color(0xFF3EA6FF),
            modifier = Modifier.clickable(onClick = onReport),
        )
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
private fun SubtitlesPanel() {
    val medium = stringRes(R.string.text_size_medium)
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringRes(R.string.player_no_subtitles),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelectChip(text = stringRes(R.string.subtitles_off), selected = true, onClick = {})
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringRes(R.string.text_size_medium),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                stringRes(R.string.text_size_small),
                medium,
                stringRes(R.string.text_size_large),
            ).forEach { size ->
                SelectChip(text = size, selected = size == medium, onClick = {})
            }
        }
    }
}

@Composable
private fun EpisodePanel(state: PlayerUiState, onReport: () -> Unit) {
    Column(modifier = Modifier.padding(14.dp)) {
        Text(
            text = stringRes(R.string.chip_episodes),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        if (state.subtitle.isNotEmpty()) {
            Text(text = state.subtitle, fontSize = 15.sp, color = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        state.nextEpisode?.let { next ->
            Surface(
                onClick = { onReport() },
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringRes(R.string.player_next_episode),
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
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
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onUnlock() })
                },
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
                    text = stringRes(R.string.player_locked),
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
            .background(Color(0xFF05052B)),
        contentAlignment = Alignment.Center,
    ) {
        FailureContent(
            background = Color(0xFF05052B),
            message = when (failure) {
                StreamError.EXPIRED -> stringRes(R.string.link_expired)
                StreamError.OFFLINE -> stringRes(R.string.state_network_error)
                StreamError.FAILED -> stringRes(R.string.playback_failed)
            },
            alternatives = state.alternatives,
            onTry = { server -> viewModel.switchSource(server) },
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
    onTry: ((String) -> Unit)?,
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
                        onClick = { onTry(server.serverLabel) },
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
            TextButton(onClick = { onTry(alternatives.first().serverLabel) }) {
                Text(
                    text = stringRes(R.string.try_now),
                    color = Color(0xFF3EA6FF),
                    fontSize = 13.sp,
                )
            }
        }

        if (onReport != null) {
            TextButton(onClick = onReport) {
                Text(
                    text = reportLabel,
                    color = Color(0xFF3EA6FF),
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
