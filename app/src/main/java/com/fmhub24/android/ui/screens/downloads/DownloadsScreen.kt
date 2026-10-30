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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    onOpenDetails: (String) -> Unit,
    onBrowse: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val sheet by viewModel.sheetState.collectAsState()
    var selection by remember { mutableStateOf(setOf<String>()) }
    var menuFor by remember { mutableStateOf<DownloadRow?>(null) }
    var confirmDelete by remember { mutableStateOf<DownloadRow?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }

    if (openGroup != null) {
        GroupDetail(
            contentId = openGroup.orEmpty(),
            onBack = { openGroup = null },
            viewModel = viewModel,
            onOpenDetails = onOpenDetails,
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
                                onOpen = { onOpenDetails(row.record.contentId) },
                                onPause = { viewModel.setPaused(row.record.id, !it) },
                                onRetry = { viewModel.retryRow(row) },
                                onDelete = { confirmDelete = row },
                                onMenu = { menuFor = row },
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
                    item(key = "group-$contentId") {
                        if (selection.isEmpty()) {
                            GroupRow(
                                row = representative,
                                episodeCount = groupRows.size,
                                totalBytes = groupRows.sumOf { it.sizeBytes ?: 0L },
                                onClick = { openGroup = contentId },
                                onMenu = {
                                    menuFor = groupRows.firstOrNull { it.record.episodeKey == null }
                                        ?: representative
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
                    DangerButton(
                        text = stringRes(R.string.delete_with_size, formatBytes(totalSelected)),
                        onClick = { selectedRows.firstOrNull()?.let { confirmDelete = it } },
                    )
                }
            }
        }
    }

    menuFor?.let { row ->
        RowMenu(
            isSeries = row.record.episodeKey != null,
            onDismiss = { menuFor = null },
            onPlay = {
                menuFor = null
                onOpenDetails(row.record.contentId)
            },
            onDetails = {
                menuFor = null
                onOpenDetails(row.record.contentId)
            },
            onDelete = {
                confirmDelete = row
                menuFor = null
            },
        )
    }

    confirmDelete?.let { row ->
        DeleteConfirmation(
            size = row.sizeBytes,
            onConfirm = {
                viewModel.remove(row.record.id)
                selection = selection - row.record.id
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }

    sheet?.let { state ->
        DownloadOptionsSheet(
            sheet = state,
            viewModel = viewModel,
            onPlay = { id, _ -> onOpenDetails(id) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieRow(
    row: DownloadRow,
    onOpen: () -> Unit,
    onPause: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onMenu),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(84.dp)) {
            LandscapeThumb(
                url = row.record.posterUrl,
                contentDescription = row.record.title,
                progress = if (row.isInProgress) row.percent else null,
            )
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
                    row.sizeBytes?.let { formatBytes(it) },
                ).joinToString(" · "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (row.state?.phase == DownloadState.Phase.DOWNLOADING) {
                Spacer(Modifier.height(6.dp))
                ThinProgress(progress = row.percent)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringRes(
                            R.string.download_in_progress,
                            (row.percent * 100).toInt(),
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onPause(true) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Pause,
                            contentDescription = stringRes(R.string.action_pause),
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
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringRes(R.string.action_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupRow(
    row: DownloadRow,
    episodeCount: Int,
    totalBytes: Long,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(84.dp)) {
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
                .combinedClickable(onClick = onClick, onLongClick = onMenu),
        ) {
            Text(
                text = row.record.title,
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
        IconButton(onClick = onMenu) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun RowMenu(
    isSeries: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringRes(R.string.menu_play)) },
            onClick = onPlay,
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.menu_details)) },
            onClick = onDetails,
        )
        DropdownMenuItem(
            text = { Text(stringRes(R.string.menu_delete)) },
            onClick = onDelete,
        )
        if (isSeries) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.menu_delete_season)) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun DeleteConfirmation(size: Long?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringRes(R.string.delete_confirm_title)) },
        text = {
            Text(
                text = if (size != null) {
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

/** Series group detail: season chips and the episode list (spec 6.8). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupDetail(
    contentId: String,
    onBack: () -> Unit,
    viewModel: DownloadsViewModel,
    onOpenDetails: (String) -> Unit,
) {
    val rows by viewModel.rowsForGroup(contentId).collectAsState()
    var season by remember { mutableStateOf<Int?>(null) }
    val seasons = rows.mapNotNull { it.record.seasonNumber }.distinct().sorted()
    val current = season ?: seasons.firstOrNull()
    val visible = rows.filter { it.record.seasonNumber == current }
    var menuRow by remember { mutableStateOf<DownloadRow?>(null) }
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
                    Icons.Outlined.Close,
                    contentDescription = stringRes(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = rows.firstOrNull()?.record?.title ?: "",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(seasons) { value ->
                    com.fmhub24.android.ui.components.SelectChip(
                        text = stringRes(R.string.season_number, value),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            text = "${row.record.episodeNumber ?: ""} · ${row.record.title}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(
                                row.record.quality?.let { "${it}p" },
                                row.sizeBytes?.let { formatBytes(it) },
                            ).joinToString(" · "),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (row.isFinished) {
                        Icon(
                            Icons.Outlined.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else if (row.isInProgress) {
                        InlineSpinner(size = 16.dp)
                    }
                    IconButton(onClick = { menuRow = row }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    menuRow?.let { row ->
        RowMenu(
            isSeries = true,
            onDismiss = { menuRow = null },
            onPlay = {
                menuRow = null
                onOpenDetails(row.record.contentId)
            },
            onDetails = {
                menuRow = null
                onOpenDetails(row.record.contentId)
            },
            onDelete = {
                confirm = row
                menuRow = null
            },
        )
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
