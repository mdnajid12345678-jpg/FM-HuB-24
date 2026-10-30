@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.fmhub24.android.ui.screens.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.fmhub24.android.R
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.ui.FmHubLoader
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.LiveBadge
import com.fmhub24.android.ui.components.RemoteImage
import com.fmhub24.android.ui.components.SecondaryButton
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.stream.StreamEngine

/**
 * Channel page (spec 6.3): 16:9 player on top with a LIVE badge, no seek bar and no download;
 * play/pause, quality, picture in picture and full screen; below the name, category, Favorite and
 * Server buttons and a row of other channels. Full screen adds the Channels side panel.
 */
@Composable
fun ChannelScreen(
    contentId: String,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sources by viewModel.channelSourcesState.collectAsState()
    val sourcesLoading by viewModel.sourcesLoadingState.collectAsState()
    val channels = state.channels
    val channel = channels.firstOrNull { it.id == contentId }
    val isFavorite by remember(contentId) { viewModel.isFavorite(contentId) }.collectAsState()

    val context = LocalContext.current
    val activity = context.findActivity()

    var playing by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var showChannelsPanel by remember { mutableStateOf(true) }
    var showServerSheet by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableStateOf<String?>(null) }

    val source = remember(sources, selectedServer) {
        sources.firstOrNull { it.serverLabel == selectedServer } ?: sources.firstOrNull()
    }

    LaunchedEffect(contentId) {
        selectedServer = null
        viewModel.loadSources(contentId)
    }

    DisposableEffect(fullscreen) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = previous
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val player = remember(source?.mediaUrl) {
        val current = source ?: return@remember null
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(StreamEngine.dataSourceFactory(current.headers)),
            )
            .build()
            .apply {
                setMediaItem(
                    androidx.media3.common.MediaItem.Builder()
                        .setUri(current.mediaUrl)
                        .setMimeType(StreamEngine.mimeTypeFor(current))
                        .build(),
                )
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    LaunchedEffect(player, playing) {
        if (playing) player?.play() else player?.pause()
    }

    if (channel == null && !state.loading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = stringRes(R.string.empty_live_title),
                detail = stringRes(R.string.empty_live_detail),
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                if (player != null) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply { player.setVideoSurfaceView(this) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    RemoteImage(
                        url = channel?.posterUrl,
                        contentDescription = channel?.title,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                    LiveBadge()
                }

                if (sourcesLoading || source == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FmHubLoader(Modifier.size(40.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { playing = !playing }) {
                        Icon(
                            if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = stringRes(
                                if (playing) R.string.action_pause else R.string.action_play,
                            ),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    TextButton(onClick = { showServerSheet = true }) {
                        Text(
                            text = stringRes(R.string.chip_quality),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { enterPip(activity) }) {
                        Icon(
                            Icons.Outlined.PictureInPictureAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = {
                        fullscreen = !fullscreen
                        showChannelsPanel = true
                    }) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit
                            else Icons.Outlined.Fullscreen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (!fullscreen) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = channel?.title ?: "",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val labels = channel?.genres?.take(2)?.joinToString(" · ").orEmpty()
                    if (labels.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = labels,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Button(
                            onClick = { channel?.let(viewModel::toggleFavorite) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringRes(
                                    if (isFavorite) R.string.unfavorite_action
                                    else R.string.favorite_action,
                                ),
                                fontSize = 13.sp,
                            )
                        }
                        SecondaryButton(
                            text = stringRes(R.string.server_button),
                            onClick = { showServerSheet = true },
                        )
                    }

                    val others = channels.filter { it.id != contentId }
                    if (others.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = stringRes(R.string.other_channels),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(others, key = { it.id }) { other ->
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable { onOpenChannel(other.id) },
                                ) {
                                    RemoteImage(
                                        url = other.posterUrl,
                                        contentDescription = other.title,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(72.dp),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = other.title,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (fullscreen && showChannelsPanel) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(230.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringRes(R.string.channels_side_panel),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showChannelsPanel = false }) {
                        Text(stringRes(R.string.action_close), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(channels, key = { it.id }) { other ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onOpenChannel(other.id) },
                        ) {
                            RemoteImage(
                                url = other.posterUrl,
                                contentDescription = other.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(74.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = other.title,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (fullscreen && !showChannelsPanel) {
            TextButton(
                onClick = { showChannelsPanel = true },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = stringRes(R.string.channels_side_panel),
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (showServerSheet) {
        ServerSheet(
            servers = sources,
            loading = sourcesLoading,
            current = source?.serverLabel,
            onPick = { label ->
                selectedServer = label
                showServerSheet = false
            },
            onDismiss = { showServerSheet = false },
        )
    }
}

/** Server picker for a live channel (the Server button, spec 6.3). */
@Composable
private fun ServerSheet(
    servers: List<MediaSource>,
    loading: Boolean,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.server_button)) },
        text = {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FmHubLoader(Modifier.size(40.dp))
                }
                servers.isEmpty() -> Text(
                    text = stringRes(R.string.no_playable_source),
                    fontSize = 13.sp,
                )
                else -> Column {
                    servers.forEach { server ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(server.serverLabel) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = current == server.serverLabel,
                                onClick = { onPick(server.serverLabel) },
                            )
                            Text(
                                text = listOfNotNull(
                                    server.serverLabel,
                                    server.codecLabel,
                                ).joinToString(" · "),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.action_done)) }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun enterPip(activity: Activity?) {
    activity ?: return
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        runCatching { activity.enterPictureInPictureMode() }
    }
}
