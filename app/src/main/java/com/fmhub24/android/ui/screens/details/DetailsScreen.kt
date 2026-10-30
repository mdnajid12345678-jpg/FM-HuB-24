@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fmhub24.android.ui.screens.details

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.model.EpisodeItem
import com.fmhub24.android.data.model.MediaItem
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.DesignChip
import com.fmhub24.android.ui.components.ErrorState
import com.fmhub24.android.ui.components.InlineSpinner
import com.fmhub24.android.ui.components.LandscapeThumb
import com.fmhub24.android.ui.components.LoadingState
import com.fmhub24.android.ui.components.PosterCard
import com.fmhub24.android.ui.components.RemoteImage
import com.fmhub24.android.ui.components.SecondaryButton
import com.fmhub24.android.ui.components.SectionHeader
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.formatBytes
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.downloads.DownloadButtonState
import com.fmhub24.android.ui.screens.downloads.DownloadOptionsSheet
import com.fmhub24.android.ui.screens.downloads.DownloadsViewModel

/**
 * Details for a single item and for a series (spec 6.5 and 6.6): banner, tags, the language and
 * server button, play/download, description, similar row, and the season/episode list.
 */
@Composable
fun DetailsScreen(
    contentId: String,
    onBack: () -> Unit,
    onPlay: (String, String?) -> Unit,
    onOpenChannel: (String) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel(),
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sources by viewModel.sourceState.collectAsState()
    val context = LocalContext.current
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showSeasonSheet by remember { mutableStateOf(false) }
    var expandedDescription by remember { mutableStateOf(false) }

    val downloadState by downloadsViewModel.buttonState(contentId).collectAsState(
        initial = DownloadButtonState.Idle,
    )

    when {
        state.loading && state.item == null -> LoadingState()
        state.item == null -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErrorState(
                message = dataErrorMessage(state.error),
                onRetry = viewModel::load,
            )
        }
        else -> {
            val item = state.item ?: return

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Banner(
                        item = item,
                        onBack = onBack,
                        inMyList = state.inMyList,
                        onToggleList = viewModel::toggleMyList,
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    context.getString(R.string.share_text_title, item.title) + "\n" +
                                        context.getString(R.string.share_link_label),
                                )
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        onPlay = {
                            if (item.isLive) onOpenChannel(item.id) else onPlay(item.id, null)
                        },
                    )
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = item.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.releaseYear?.let { Tag(it.toString()) }
                            Tag(typeLabel(item))
                            if (item.usesEpisodes && state.seasons.size > 1) {
                                Tag(stringRes(R.string.seasons_count, state.seasons.size))
                            }
                            item.genres.take(2).forEach { genre -> Tag(genre) }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (item.usesEpisodes) {
                            SeriesActions(
                                state = state,
                                sources = sources,
                                onPlayContinue = { episode ->
                                    onPlay(item.id, episode?.key)
                                },
                                onSeasonDownload = { showSeasonSheet = true },
                                onRefreshSources = { viewModel.loadSources() },
                            )
                        } else {
                            MovieActions(
                                state = state,
                                sources = sources,
                                downloadState = downloadState,
                                onOpenLanguageSheet = { showLanguageSheet = true },
                                onPlay = { onPlay(item.id, null) },
                                onDownload = {
                                    downloadsViewModel.openSheet(item, episode = null)
                                },
                                onRefreshSources = { viewModel.loadSources() },
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        if (!item.description.isNullOrBlank()) {
                            Text(
                                text = item.description.orEmpty(),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (expandedDescription) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.description.length > 120) {
                                Text(
                                    text = stringRes(
                                        if (expandedDescription) R.string.details_less
                                        else R.string.details_more,
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        expandedDescription = !expandedDescription
                                    },
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }

                if (item.usesEpisodes) {
                    item {
                        SeasonSelector(
                            seasons = state.seasons,
                            selectedSeason = state.selectedSeason,
                            episodeCounts = state.episodes.groupingBy { it.seasonNumber }.eachCount(),
                            onSelect = viewModel::selectSeason,
                        )
                    }
                    item {
                        EpisodeList(
                            episodes = viewModel.episodesFor(state.selectedSeason),
                            history = state.history,
                            onClick = { episode -> onPlay(item.id, episode.key) },
                            onDownload = { episode ->
                                downloadsViewModel.openSheet(item, episode = episode)
                            },
                        )
                    }
                }

                item {
                    SimilarSection(
                        contentId = item.id,
                        similar = viewModel.similar.collectAsState().value,
                        onOpenDetails = onOpenChannel,
                    )
                }
            }
        }
    }

    if (showLanguageSheet) {
        LanguageServerSheet(
            languages = sources.languages,
            currentLanguage = sources.languageKey,
            servers = viewModel.playableServers(),
            currentServer = sources.serverLabel,
            onLanguageSelected = viewModel::selectLanguage,
            onServerSelected = viewModel::selectServer,
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showSeasonSheet) {
        val item = state.item ?: return
        SeasonDownloadSheet(
            seasons = state.seasons,
            episodeCounts = state.episodes.groupingBy { it.seasonNumber }.eachCount(),
            onDismiss = { showSeasonSheet = false },
            onSeasonDownload = { seasonNumber ->
                val episodes = viewModel.episodesFor(seasonNumber)
                downloadsViewModel.openSheet(item, episode = null, seasonEpisodes = episodes)
                showSeasonSheet = false
            },
        )
    }

    downloadsViewModel.sheetState.collectAsState().value?.let { sheet ->
        DownloadOptionsSheet(sheet = sheet, viewModel = downloadsViewModel, onPlay = { id, key ->
            onPlay(id, key)
        })
    }
}

@Composable
private fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.68f),
        )
    }
}

private fun typeLabel(item: MediaItem): String = when (item.contentType) {
    com.fmhub24.android.data.model.ContentType.MOVIE,
    com.fmhub24.android.data.model.ContentType.ANIME_MOVIE,
    -> item.contentType.name
    else -> item.contentType.name
}.replace('_', ' ').uppercase()

@Composable
private fun Banner(
    item: MediaItem,
    onBack: () -> Unit,
    inMyList: Boolean,
    onToggleList: () -> Unit,
    onShare: () -> Unit,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        RemoteImage(
            url = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x9905052B), Color(0xE605052B)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringRes(R.string.action_back),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = stringRes(R.string.action_share),
                    tint = Color.White,
                )
            }
                IconButton(onClick = onToggleList) {
                    Icon(
                        if (inMyList) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkAdd,
                    contentDescription = stringRes(
                        if (inMyList) R.string.details_in_my_list else R.string.details_add_to_list,
                    ),
                    tint = Color.White,
                )
            }
        }
        Surface(
            onClick = onPlay,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
        ) {
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = stringRes(R.string.action_play),
                tint = Color.White,
                modifier = Modifier.padding(8.dp).size(28.dp),
            )
        }
    }
}

/** The wide language and server button plus Play and Download (spec 6.5). */
@Composable
private fun MovieActions(
    state: DetailsUiState,
    sources: SourcesUiState,
    downloadState: DownloadButtonState,
    onOpenLanguageSheet: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onRefreshSources: () -> Unit,
) {
    val languageLabel = sources.languageKey?.let { SourceSelection.languageLabel(it, false) }
    val label = when {
        sources.loading -> stringRes(R.string.checking_source)
        sources.error != null -> stringRes(R.string.no_playable_source)
        sources.sources.isEmpty() -> stringRes(R.string.no_playable_source)
        else -> listOfNotNull(
            languageLabel,
            sources.serverLabel ?: stringRes(R.string.auto_option),
        ).joinToString(" · ")
    }

    Surface(
        onClick = onOpenLanguageSheet,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sources.loading) {
                InlineSpinner(size = 16.dp)
            } else {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (sources.error != null && sources.sources.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        ErrorState(message = dataErrorMessage(sources.error), onRetry = onRefreshSources)
    }

    Spacer(Modifier.height(12.dp))
    // Design: full-width Play plus a square icon-only download button.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = sources.sources.isNotEmpty(),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(R.string.action_play), fontSize = 14.sp)
        }
        androidx.compose.material3.OutlinedButton(
            onClick = onDownload,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = sources.sources.isNotEmpty() &&
                downloadState !is DownloadButtonState.Running,
        ) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = stringRes(R.string.action_download),
                modifier = Modifier.size(20.dp),
            )
        }
    }
    if (downloadState is DownloadButtonState.Completed) {
        Text(
            text = stringRes(R.string.download_completed),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun DownloadButtonState.label(): String = when (this) {
    is DownloadButtonState.Idle -> stringRes(R.string.action_download)
    is DownloadButtonState.Checking -> stringRes(R.string.download_checking)
    is DownloadButtonState.Running -> stringRes(
        R.string.download_in_progress,
        (percent * 100).toInt(),
    )
    is DownloadButtonState.Paused -> stringRes(R.string.download_paused)
    is DownloadButtonState.Completed -> stringRes(R.string.download_completed)
    is DownloadButtonState.Failed -> stringRes(R.string.download_failed)
}

/** Series primary button: Play S1 E1 / Continue S1 E5 plus the season download icon. */
@Composable
private fun SeriesActions(
    state: DetailsUiState,
    sources: SourcesUiState,
    onPlayContinue: (EpisodeItem?) -> Unit,
    onSeasonDownload: () -> Unit,
    onRefreshSources: () -> Unit,
) {
    val episode = state.history?.let { history ->
        state.episodes.firstOrNull {
            it.seasonNumber == history.seasonNumber && it.episodeNumber == history.episodeNumber
        }
    } ?: state.episodes.minByOrNull { it.seasonNumber * 10_000 + it.episodeNumber }

    if (state.episodes.isEmpty() && !state.loading) {
        EmptyState(
            title = stringRes(R.string.no_episodes),
            detail = stringRes(R.string.empty_home_detail),
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.Button(
            onClick = { onPlayContinue(episode) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(10.dp),
            enabled = episode != null && sources.sources.isNotEmpty(),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (state.history != null && episode != null) {
                    stringRes(
                        R.string.continue_episode,
                        episode.seasonNumber,
                        episode.episodeNumber,
                    )
                } else if (episode != null) {
                    stringRes(R.string.play_episode, episode.seasonNumber, episode.episodeNumber)
                } else {
                    stringRes(R.string.action_play)
                },
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
        androidx.compose.material3.IconButton(
            onClick = onSeasonDownload,
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(10.dp),
                ),
            enabled = state.episodes.isNotEmpty(),
        ) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = stringRes(R.string.season_download_title),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (sources.loading) {
        Spacer(Modifier.height(8.dp))
        InlineSpinner(size = 16.dp)
    } else if (sources.error != null && sources.sources.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        ErrorState(message = dataErrorMessage(sources.error), onRetry = onRefreshSources)
    }
}

/*
 * Season picker: one chip per season, tappable to switch the episode list (spec 6.6), with the
 * episode count of the season that is now selected underneath.
 */
@Composable
private fun SeasonSelector(
    seasons: List<com.fmhub24.android.data.model.SeasonItem>,
    selectedSeason: Int?,
    episodeCounts: Map<Int, Int>,
    onSelect: (Int) -> Unit,
) {
    if (seasons.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                seasons.forEach { season ->
                    DesignChip(
                        text = season.name?.takeIf { it.isNotBlank() }
                            ?: stringRes(R.string.season_number, season.seasonNumber),
                        selected = season.seasonNumber == selectedSeason,
                        onClick = { onSelect(season.seasonNumber) },
                    )
                }
            }
        }
        val count = episodeCounts[selectedSeason] ?: 0
        if (count > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringRes(
                    R.string.season_with_episodes,
                    count,
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<EpisodeItem>,
    history: com.fmhub24.android.data.repo.HistoryEntry?,
    onClick: (EpisodeItem) -> Unit,
    onDownload: (EpisodeItem) -> Unit,
) {
    if (episodes.isEmpty()) {
        EmptyState(
            title = stringRes(R.string.no_episodes),
            detail = stringRes(R.string.empty_home_detail),
        )
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        episodes.forEach { episode ->
            val matchesHistory = history?.seasonNumber == episode.seasonNumber &&
                history.episodeNumber == episode.episodeNumber
            val progress = if (matchesHistory) history?.progress ?: 0f else 0f
            val watched = matchesHistory && progress >= 0.92f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(episode) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.width(96.dp)) {
                    LandscapeThumb(
                        url = episode.posterUrl,
                        contentDescription = episode.title,
                        progress = if (progress > 0f && !watched) progress else null,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringRes(R.string.episode_number, episode.episodeNumber)} · " +
                            (episode.title ?: ""),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Status only when there is one: watched, the resume point, or the synopsis.
                    val subtitle = when {
                        watched -> stringRes(R.string.watched)
                        matchesHistory && progress > 0f -> stringRes(
                            R.string.progress_percent,
                            (progress * 100).toInt(),
                        )
                        !episode.description.isNullOrBlank() -> episode.description.orEmpty()
                        else -> ""
                    }
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = if (watched) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Design: watched ✓ in green, an in-progress share in blue, otherwise a download ⤓.
                when {
                    watched -> Icon(
                        Icons.Outlined.Check,
                        contentDescription = stringRes(R.string.watched),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    matchesHistory && progress > 0f -> Text(
                        text = stringRes(R.string.progress_percent, (progress * 100).toInt()),
                        fontSize = 12.sp,
                        color = Color(0xFF7DB0FF),
                    )
                    else -> IconButton(
                        onClick = { onDownload(episode) },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = stringRes(R.string.action_download),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarSection(
    contentId: String,
    similar: List<MediaItem>,
    onOpenDetails: (String) -> Unit,
) {
    val items = remember(similar, contentId) { similar.filter { it.id != contentId }.take(6) }
    if (items.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        SectionHeader(title = stringRes(R.string.details_similar))
        Spacer(Modifier.height(10.dp))
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpenDetails(item.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
