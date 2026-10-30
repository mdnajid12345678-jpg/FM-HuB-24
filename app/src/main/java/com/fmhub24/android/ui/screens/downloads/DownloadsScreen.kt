package com.fmhub24.android.ui.screens.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.repo.DownloadRow
import com.fmhub24.android.ui.components.DangerButton
import com.fmhub24.android.ui.components.EmptyState
import com.fmhub24.android.ui.components.InlineSpinner
import com.fmhub24.android.ui.components.LandscapeThumb
import com.fmhub24.android.ui.components.RemoteImage
import com.fmhub24.android.ui.components.SecondaryButton
import com.fmhub24.android.ui.components.ThinProgress
import com.fmhub24.android.ui.components.formatBytes
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.stream.DownloadState

/**
 * Downloads tab (spec 6.8): storage used in the header, movie rows, series groups, in-progress
 * rows with pause, failed rows with retry, long-press selection mode with a confirming Delete.
 * There is deliberately no "Delete all".
 */
private data class DeleteRequest(
    val rows: List<DownloadRow>,
    val contentId: String? = null,
)

private data class RenameRequest(
    val contentId: String,
    val downloadId: String?,
    val title: String,
    val isSeries: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    onOpenDetails: (String) -> Unit,
    onPlay: (String, String?) -> Unit,
    onBrowse: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val sheet by viewModel.sheetState.collectAsState()
    var selection by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf<DeleteRequest?>(null) }
    var renameRequest by remember { mutableStateOf<RenameRequest?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }

    if (openGroup != null) {
        GroupDetail(
            contentId = openGroup.orEmpty(),
            onBack = { openGroup = null },
            viewModel = viewModel,
            onOpenDetails = onOpenDetails,
            onPlay = onPlay,
        )
        return
    }

    val selectedRows = rows.filter { it.record.id in selection }
    val totalSelected = selectedRows.sumOf { it.sizeBytes ?: 0L }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selection.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.tab_downloads),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringRes(
                        R.string.downloads_storage_used,
                        formatBytes(rows.sumOf { it.sizeBytes ?: 0L }),
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selection = emptySet() }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringRes(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringRes(R.string.selection_mode_count, selection.size),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { selection = rows.map { it.record.id }.toSet() }) {
                    Text(stringRes(R.string.select_all), fontSize = 12.sp)
                }
            }
        }

        if (rows.isEmpty()) {
            EmptyState(
                title = stringRes(R.string.empty_downloads_title),
                detail = stringRes(R.string.empty_downloads_detail),
                icon = Icons.Outlined.Download,
                actionLabel = stringRes(R.string.browse_action),
                onAction = onBrowse,
            )
        } else {
            val movieRows = rows.filter { it.record.episodeKey == null }
            val episodeRows = rows.filter { it.record.episodeKey != null }
            val groups = episodeRows.groupBy { it.record.contentId }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                movieRows.forEach { row ->
                    item(key = row.record.id) {
                        if (selection.isEmpty()) {
                            MovieRow(
                                row = row,
                                onOpen = { onPlay(row.record.contentId, null) },
                                onPause = { viewModel.setPaused(row.record.id, true) },
                                onResume = { viewModel.setPaused(row.record.id, false) },
                                onRetry = { viewModel.retryRow(row) },
                                onDelete = { confirmDelete = DeleteRequest(listOf(row)) },
                                onDetails = { onOpenDetails(row.record.contentId) },
                                onRename = {
                                    renameRequest = RenameRequest(
                                        contentId = row.record.contentId,
                                        downloadId = row.record.id,
                                        title = row.record.title,
                                        isSeries = false,
                                    )
                                },
                                onLongPress = { selection = setOf(row.record.id) },
                            )
                        } else {
                            SelectableRow(row = row, selection = selection) { id ->
                                selection = if (id in selection) selection - id else selection + id
                            }
                        }
                    }
                }

                groups.forEach { (contentId, groupRows) ->
                    val representative = groupRows.first()
                    val firstEpisode = groupRows.minWithOrNull(
                        compareBy<DownloadRow> { it.record.seasonNumber ?: Int.MAX_VALUE }
                            .thenBy { it.record.episodeNumber ?: Int.MAX_VALUE },
                    )
                    item(key = "group-$contentId") {
                        if (selection.isEmpty()) {
                            GroupRow(
                                row = representative,
                                episodeCount = groupRows.size,
                                totalBytes = groupRows.sumOf { it.sizeBytes ?: 0L },
                                onPlayFirst = {
                                    firstEpisode?.record?.episodeKey?.let { onPlay(contentId, it) }
                                },
                                onOpenEpisodes = { openGroup = contentId },
                                onDelete = { confirmDelete = DeleteRequest(groupRows, contentId) },
                                onRename = {
                                    renameRequest = RenameRequest(
                                        contentId = contentId,
                                        downloadId = null,
                                        title = seriesTitle(representative.record.title),
                                        isSeries = true,
                                    )
                                },
                                onLongPress = {
                                    selection = groupRows.map { it.record.id }.toSet()
                                },
                            )
                        } else {
                            SelectableRow(
                                row = representative.copy(
                                    record = representative.record.copy(
                                        title = representative.record.title,
                                    ),
                                ),
                                selection = selection,
                            ) { id ->
                                val ids = groupRows.map { it.record.id }
                                selection = if (id in selection) selection - ids.toSet()
                                else selection + ids.toSet()
                            }
                        }
                    }
                }

                if (selection.isEmpty()) {
                    item(key = "long-press-hint") {
                        Text(
                            text = stringRes(R.string.design_hint_multi_select),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        if (selection.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringRes(R.string.selection_mode_count, selection.size) +
                            " · " + formatBytes(totalSelected),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    DangerButton(
                        text = stringRes(R.string.delete_with_size, formatBytes(totalSelected)),
                        onClick = {
                            if (selectedRows.isNotEmpty()) confirmDelete = DeleteRequest(selectedRows)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    confirmDelete?.let { request ->
        DeleteConfirmation(
            size = request.rows.sumOf { it.sizeBytes ?: 0L },
            count = request.rows.size,
            isSeries = request.contentId != null,
            onConfirm = {
                if (request.contentId != null) {
                    viewModel.removeGroup(request.contentId)
                } else {
                    request.rows.forEach { viewModel.remove(it.record.id) }
                }
                selection = selection - request.rows.map { it.record.id }.toSet()
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    renameRequest?.let { request ->
        RenameDialog(
            request = request,
            onSave = { title ->
                if (request.isSeries) viewModel.renameSeries(request.contentId, title)
                else request.downloadId?.let { viewModel.renameDownload(it, title) }
                renameRequest = null
            },
            onDismiss = { renameRequest = null },
        )
    }

    sheet?.let { state ->
        DownloadOptionsSheet(
            sheet = state,
            viewModel = viewModel,
            onPlay = { id, key -> onPlay(id, key) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieRow(
    row: DownloadRow,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onLongPress: () -> Unit,
) {
    var menuExpanded by remember(row.record.id) { mutableStateOf(false) }
    val phase = row.state?.phase
    val byteLabel = row.state?.let { live ->
        if (live.bytesTotal > 0L) {
            "${formatBytes(live.bytesDownloaded)} / ${formatBytes(live.bytesTotal)}"
        } else {
            formatBytes(live.bytesDownloaded)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(44.dp)) {
            RemoteImage(
                url = row.record.posterUrl,
                contentDescription = row.record.title,
                aspectRatio = 2f / 3f,
                modifier = Modifier.fillMaxWidth(),
            )
            if (row.isInProgress) {
                ThinProgress(
                    progress = row.percent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.record.title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    row.record.quality?.let { "${it}p" },
                    row.record.language,
                    byteLabel ?: row.sizeBytes?.let { formatBytes(it) },
                ).joinToString(" · "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (phase == DownloadState.Phase.DOWNLOADING ||
                phase == DownloadState.Phase.PAUSED || phase == DownloadState.Phase.QUEUED
            ) {
                Spacer(Modifier.height(6.dp))
                ThinProgress(progress = row.percent)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (phase) {
                            DownloadState.Phase.PAUSED -> stringRes(R.string.download_paused)
                            DownloadState.Phase.QUEUED -> stringRes(R.string.download_queued)
                            else -> stringRes(
                                R.string.download_in_progress,
                                (row.percent * 100).toInt(),
                            )
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = if (phase == DownloadState.Phase.PAUSED) onResume else onPause,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            if (phase == DownloadState.Phase.PAUSED) {
                                Icons.Outlined.PlayArrow
                            } else Icons.Outlined.Pause,
                            contentDescription = stringRes(
                                if (phase == DownloadState.Phase.PAUSED) R.string.action_resume
                                else R.string.action_pause,
                            ),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else if (row.isFailed) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = onRetry) {
                        Text(stringRes(R.string.download_retry), fontSize = 12.sp)
                    }
                }
            } else if (row.isFinished) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringRes(R.string.download_completed),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringRes(R.string.action_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_play)) },
                    onClick = { menuExpanded = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_details)) },
                    onClick = { menuExpanded = false; onDetails() },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_rename)) },
                    onClick = { menuExpanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_delete)) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    row: DownloadRow,
    episodeCount: Int,
    totalBytes: Long,
    onPlayFirst: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onLongPress: () -> Unit,
) {
    var menuExpanded by remember(row.record.contentId) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(84.dp).clickable(onClick = onPlayFirst)) {
            LandscapeThumb(
                url = row.record.posterUrl,
                contentDescription = row.record.title,
            )
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .clickable(onClick = onOpenEpisodes)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(4.dp),
                    ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onPlayFirst, onLongClick = onLongPress),
        ) {
            Text(
                text = seriesTitle(row.record.title),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$episodeCount · ${formatBytes(totalBytes)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringRes(R.string.action_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_play)) },
                    onClick = { menuExpanded = false; onPlayFirst() },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_open_episodes)) },
                    onClick = { menuExpanded = false; onOpenEpisodes() },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_rename)) },
                    onClick = { menuExpanded = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.menu_delete_series)) },
                    onClick = { menuExpanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun SelectableRow(
    row: DownloadRow,
    selection: Set<String>,
    onToggle: (String) -> Unit,
) {
    val selected = row.record.id in selection
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp),
            )
            .clickable { onToggle(row.record.id) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(
            checked = selected,
            onCheckedChange = { onToggle(row.record.id) },
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.width(72.dp)) {
            LandscapeThumb(url = row.record.posterUrl, contentDescription = row.record.title)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.record.title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.record.episodeKey?.substringAfterLast('|')?.replace(':', ' ') ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeleteConfirmation(
    size: Long?,
    count: Int = 1,
    isSeries: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.delete_confirm_title)) },
        text = {
            Text(
                text = if (isSeries && size != null) {
                    stringRes(R.string.delete_series_confirm_message, formatBytes(size))
                } else if (count > 1 && size != null) {
                    stringRes(R.string.delete_selected_confirm_message, count, formatBytes(size))
                } else if (size != null) {
                    stringRes(R.string.delete_confirm_message, formatBytes(size))
                } else {
                    stringRes(R.string.delete_confirm_generic)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringRes(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun RenameDialog(
    request: RenameRequest,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(request.contentId, request.downloadId) {
        mutableStateOf(request.title)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringRes(
                    if (request.isSeries) R.string.rename_series_title
                    else R.string.rename_download_title,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120) },
                singleLine = true,
                label = {
                    Text(
                        stringRes(
                            if (request.isSeries) R.string.rename_series_hint
                            else R.string.rename_download_hint,
                        ),
                    )
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringRes(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.action_cancel))
            }
        },
    )
}

private fun seriesTitle(title: String): String = title.substringBefore(" · S").trim()

private fun episodeTitle(title: String, season: Int?, episode: Int?): String {
    val marker = "S${season ?: 0}E${episode ?: 0}"
    return title.substringAfter(" · $marker", "")
        .removePrefix(" · ")
        .trim()
        .ifBlank { "Episode ${episode ?: ""}".trim() }
}

/** Series group detail: season chips and the episode list (spec 6.8). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupDetail(
    contentId: String,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel,
    onOpenDetails: (String) -> Unit,
    onPlay: (String, String?) -> Unit,
) {
    val rows by viewModel.rowsForGroup(contentId).collectAsState()
    var season by remember { mutableStateOf<Int?>(null) }
    val seasons = rows.mapNotNull { it.record.seasonNumber }.distinct().sorted()
    val current = season ?: seasons.firstOrNull()
    val visible = rows.filter { it.record.seasonNumber == current }
    var confirm by remember { mutableStateOf<DownloadRow?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringRes(R.string.action_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = rows.firstOrNull()?.record?.title?.let(::seriesTitle).orEmpty(),
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringRes(R.string.season_with_episodes, rows.size) + " · " +
                formatBytes(rows.sumOf { it.sizeBytes ?: 0L }),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (seasons.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(seasons) { value ->
                    val count = rows.count { it.record.seasonNumber == value }
                    com.fmhub24.android.ui.components.SelectChip(
                        text = "${stringRes(R.string.season_number, value)} ($count)",
                        selected = value == current,
                        onClick = { season = value },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.record.id }) { row ->
                var menuExpanded by remember(row.record.id) { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onPlay(row.record.contentId, row.record.episodeKey)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(110.dp)) {
                        LandscapeThumb(
                            url = row.record.posterUrl,
                            contentDescription = row.record.title,
                            progress = if (row.isInProgress) row.percent else null,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episodeTitle(
                                row.record.title,
                                row.record.seasonNumber,
                                row.record.episodeNumber,
                            ),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(
                                row.record.quality?.let { "${it}p" },
                                row.state?.let { live ->
                                    if (live.bytesTotal > 0L) {
                                        "${formatBytes(live.bytesDownloaded)} / ${formatBytes(live.bytesTotal)}"
                                    } else formatBytes(live.bytesDownloaded)
                                } ?: row.sizeBytes?.let { formatBytes(it) },
                            ).joinToString(" · "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when (row.state?.phase) {
                        DownloadState.Phase.COMPLETED -> Icon(
                            Icons.Outlined.DownloadDone,
                            contentDescription = stringRes(R.string.download_completed),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        DownloadState.Phase.PAUSED -> IconButton(
                            onClick = { viewModel.setPaused(row.record.id, false) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = stringRes(R.string.action_resume))
                        }
                        DownloadState.Phase.DOWNLOADING, DownloadState.Phase.QUEUED -> IconButton(
                            onClick = { viewModel.setPaused(row.record.id, true) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.Pause, contentDescription = stringRes(R.string.action_pause))
                        }
                        DownloadState.Phase.FAILED -> TextButton(
                            onClick = { viewModel.retryRow(row) },
                        ) {
                            Text(stringRes(R.string.download_retry), fontSize = 11.sp)
                        }
                        else -> Unit
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringRes(R.string.action_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.menu_play)) },
                                onClick = {
                                    menuExpanded = false
                                    onPlay(row.record.contentId, row.record.episodeKey)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.menu_details)) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenDetails(row.record.contentId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.menu_delete)) },
                                onClick = { menuExpanded = false; confirm = row },
                            )
                        }
                    }
                }
            }
        }
    }

    confirm?.let { row ->
        DeleteConfirmation(
            size = row.sizeBytes,
            onConfirm = {
                viewModel.remove(row.record.id)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
}
