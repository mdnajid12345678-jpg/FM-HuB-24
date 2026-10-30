package com.fmhub24.android.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.R
import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.ui.FmHubLoader
import com.fmhub24.android.ui.components.InlineSpinner
import com.fmhub24.android.ui.components.PrimaryButton
import com.fmhub24.android.ui.components.SecondaryButton
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.dataErrorMessage
import com.fmhub24.android.ui.components.formatBytes
import com.fmhub24.android.ui.components.stringRes

/**
 * Download analysis and choices (spec 6.8): "Checking available options", then language chips
 * and a version list for direct files, or tickable audio tracks and a quality row for
 * multi-language HLS, with free space and total size shown before Download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadOptionsSheet(
    sheet: DownloadSheetState,
    viewModel: DownloadsViewModel,
    onPlay: (String, String?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = viewModel::closeSheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
        ) {
            Text(
                text = sheet.item.title +
                    (sheet.episode?.let { " · E${it.episodeNumber}" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(16.dp))

            when (sheet.phase) {
                DownloadSheetState.Phase.CHECKING -> CheckingBlock()
                DownloadSheetState.Phase.ERROR -> ErrorBlock(
                    message = dataErrorMessage(sheet.error),
                    onRetry = viewModel::retry,
                    onClose = viewModel::closeSheet,
                )
                DownloadSheetState.Phase.DONE -> DoneBlock(sheet = sheet, onPlay = onPlay)
                DownloadSheetState.Phase.OPTIONS -> OptionsBlock(
                    sheet = sheet,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun CheckingBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FmHubLoader(Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringRes(R.string.checking_options),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = stringRes(R.string.action_cancel), onClick = onClose)
            PrimaryButton(text = stringRes(R.string.action_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun DoneBlock(sheet: DownloadSheetState, onPlay: (String, String?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (sheet.seasonEpisodes.isNotEmpty()) {
                stringRes(R.string.download_n_episodes, sheet.startedCount)
            } else {
                stringRes(R.string.download_started)
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringRes(R.string.action_done),
            onClick = { onPlay(sheet.item.id, sheet.episode?.key) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OptionsBlock(sheet: DownloadSheetState, viewModel: DownloadsViewModel) {
    val options = sheet.options ?: return
    val analysis = sheet.analysis

    if (sheet.seasonEpisodes.isNotEmpty()) {
        SeasonBlock(sheet = sheet, viewModel = viewModel)
    } else if (analysis != null && analysis.hasVariants) {
        HlsBlock(sheet = sheet, viewModel = viewModel)
    } else {
        VersionBlock(sheet = sheet, viewModel = viewModel)
    }

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringRes(R.string.free_space) + ": " + formatBytes(options.freeSpaceBytes),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sheet.seasonEpisodes.isNotEmpty()) {
            Text(
                text = stringRes(R.string.total_size) + ": " + stringRes(R.string.size_unknown),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        text = if (sheet.seasonEpisodes.isNotEmpty()) {
            stringRes(R.string.download_n_episodes, sheet.selectedEpisodes.size)
        } else {
            stringRes(R.string.action_download)
        },
        onClick = viewModel::start,
        enabled = !sheet.starting &&
            (sheet.seasonEpisodes.isEmpty() || sheet.selectedEpisodes.isNotEmpty()) &&
            sheet.selectedSource != null,
        modifier = Modifier.fillMaxWidth(),
    )
    if (sheet.starting) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineSpinner(size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringRes(R.string.download_started),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Language chips, then the version list with quality, codec, size and the Best badge. */
@Composable
private fun VersionBlock(sheet: DownloadSheetState, viewModel: DownloadsViewModel) {
    val options = sheet.options ?: return
    val languages = SourceSelection.languages(options.sources)
    val visible = SourceSelection.forLanguage(options.sources, sheet.selectedLanguage ?: "Original")
    val recommended = SourceSelection.recommended(visible)

    if (languages.size > 1) {
        Text(
            text = stringRes(R.string.sheet_languages),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            languages.take(5).forEach { language ->
                SelectChip(
                    text = SourceSelection.languageLabel(language, false),
                    selected = language == sheet.selectedLanguage,
                    onClick = { viewModel.selectLanguage(language) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    Text(
        text = stringRes(R.string.sheet_servers),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))

    visible.forEach { source ->
        val supported = CodecSupport.isSupported(source.codec)
        val size = sheet.sizes[viewModel.sourceKey(source)]
        val selected = sheet.selectedSource == source
        val label = listOfNotNull(
            source.serverLabel,
            SourceSelection.qualityLabel(source.quality),
            source.codecLabel,
        ).joinToString(" · ")

        // Design `.r` / `.r.on` / `.r.off`: label left, size right, unsupported rows dimmed with
        // their own caption instead of a size.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected && supported) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (selected && supported) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(10.dp),
                )
                .alpha(if (supported) 1f else 0.45f)
                .clickable(enabled = supported) { viewModel.selectSource(source) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (selected && supported) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (!supported) {
                Text(
                    text = stringRes(R.string.unsupported_on_phone),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = size?.let { formatBytes(it) } ?: stringRes(R.string.size_unknown),
                    fontSize = 11.sp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Multi-language HLS: tick audio tracks and choose a quality from the stream's variants. */
@Composable
private fun HlsBlock(sheet: DownloadSheetState, viewModel: DownloadsViewModel) {
    val analysis = sheet.analysis ?: return

    Text(
        text = stringRes(R.string.pick_audio_tracks),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    analysis.audio.filter { it.supported }.forEach { track ->
        val key = track.language ?: track.label ?: stringRes(R.string.audio_track_all)
        val label = track.label ?: track.language ?: stringRes(R.string.audio_track_all)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.toggleAudio(key) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = key in sheet.selectedAudio,
                onCheckedChange = { viewModel.toggleAudio(key) },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (analysis.video.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringRes(R.string.quality_label),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            analysis.video.forEach { option ->
                SelectChip(
                    text = "${option.height}p",
                    selected = sheet.selectedHeight == option.height,
                    onClick = { viewModel.selectHeight(option.height) },
                )
            }
        }
    }
}

/** Season download: Select all, a checkbox per episode, quality and audio rows. */
@Composable
private fun SeasonBlock(sheet: DownloadSheetState, viewModel: DownloadsViewModel) {
    val allSelected = sheet.seasonEpisodes.isNotEmpty() &&
        sheet.selectedEpisodes.size == sheet.seasonEpisodes.size
    val rows by viewModel.rows.collectAsState()
    val finished = rows.filter { it.isFinished }.mapNotNull { it.record.episodeKey }.toSet()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringRes(R.string.select_all),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { viewModel.selectAllEpisodes(!allSelected) },
        )
        Text(
            text = stringRes(R.string.selection_mode_count, sheet.selectedEpisodes.size),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))

    sheet.seasonEpisodes.forEach { episode ->
        val done = episode.key in finished
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !done) { viewModel.toggleEpisode(episode.key) }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = episode.key in sheet.selectedEpisodes,
                onCheckedChange = { viewModel.toggleEpisode(episode.key) },
                enabled = !done,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${stringRes(R.string.episode_number, episode.episodeNumber)} · " +
                    (episode.title ?: ""),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (done) {
                Text(
                    text = stringRes(R.string.download_completed),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    QualityRow(
        selected = sheet.selectedSource?.quality,
        onSelect = { height ->
            val match = sheet.options?.sources?.firstOrNull { it.quality == height }
            if (match != null) viewModel.selectSource(match)
        },
    )

    val languages = SourceSelection.languages(sheet.options?.sources ?: emptyList())
    if (languages.size > 1) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringRes(R.string.audio_label),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            languages.take(5).forEach { language ->
                SelectChip(
                    text = SourceSelection.languageLabel(language, false),
                    selected = language == sheet.selectedLanguage,
                    onClick = { viewModel.selectLanguage(language) },
                )
            }
        }
    }
}

@Composable
private fun QualityRow(selected: Int?, onSelect: (Int?) -> Unit) {
    Text(
        text = stringRes(R.string.quality_label),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        SelectChip(
            text = stringRes(R.string.quality_auto),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        listOf(360, 480, 720, 1080).forEach { quality ->
            SelectChip(
                text = "${quality}p",
                selected = selected == quality,
                onClick = { onSelect(quality) },
            )
        }
    }
}
