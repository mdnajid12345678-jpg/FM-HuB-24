@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fmhub24.android.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fmhub24.android.R
import com.fmhub24.android.data.model.CodecSupport
import com.fmhub24.android.data.model.MediaSource
import com.fmhub24.android.data.repo.SourceSelection
import com.fmhub24.android.ui.components.PrimaryButton
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.stringRes

/**
 * Language and server sheet (spec 6.5): language chips, then the server list with Auto marked
 * Recommended, every server showing quality and codec, unsupported ones greyed, and Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageServerSheet(
    languages: List<String>,
    currentLanguage: String?,
    servers: List<MediaSource>,
    currentServer: String?,
    onLanguageSelected: (String) -> Unit,
    onServerSelected: (MediaSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringRes(R.string.language_server_button),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (languages.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    languages.take(4).forEach { language ->
                        SelectChip(
                            text = SourceSelection.languageLabel(
                                language,
                                servers.any { SourceSelection.matches(it, language) && it.isDub },
                            ),
                            selected = language == currentLanguage,
                            onClick = { onLanguageSelected(language) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringRes(R.string.sheet_servers),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            val recommended = SourceSelection.recommended(servers)
            val autoTarget = recommended ?: servers.firstOrNull()

            ServerRow(
                title = stringRes(R.string.auto_option),
                subtitle = stringRes(R.string.recommended),
                selected = currentServer == null ||
                    currentServer == stringRes(R.string.auto_option),
                supported = autoTarget != null,
                badge = stringRes(R.string.recommended),
                onClick = { autoTarget?.let(onServerSelected) },
            )

            servers.forEach { server ->
                val supported = CodecSupport.isSupported(server.codec)
                val quality = SourceSelection.qualityLabel(server.quality)
                val codec = server.codecLabel
                ServerRow(
                    title = server.serverLabel,
                    subtitle = listOfNotNull(
                        quality,
                        codec,
                        SourceSelection.languageLabel(server.language, server.isDub),
                    ).joinToString(" · "),
                    selected = currentServer == server.serverLabel,
                    supported = supported,
                    badge = if (server == recommended) stringRes(R.string.recommended) else null,
                    onClick = { if (supported) onServerSelected(server) },
                )
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = stringRes(R.string.action_done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    supported: Boolean,
    badge: String?,
    onClick: () -> Unit,
) {
    // Design `.r` / `.r.on` / `.r.off`: one line of "server · quality · codec", the selected row on
    // a translucent accent, unsupported rows dimmed with their own right-aligned caption.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !supported -> MaterialTheme.colorScheme.surfaceVariant
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
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
            .clickable(enabled = supported, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (badge != null && supported) title else "$title · $subtitle",
            fontSize = 13.sp,
            color = if (selected && supported) {
                Color(0xFFBFD8FF)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        when {
            !supported -> Text(
                text = stringRes(R.string.unsupported_on_phone),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            badge != null -> Text(
                text = badge,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
            else -> Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


/**
 * Season download sheet (spec 6.6): opened from the details download button, so every row here
 * starts a season download. Choosing which season to watch happens in the season chips above the
 * episode list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonDownloadSheet(
    seasons: List<com.fmhub24.android.data.model.SeasonItem>,
    episodeCounts: Map<Int, Int>,
    onDismiss: () -> Unit,
    onSeasonDownload: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringRes(R.string.season_download_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))

            if (seasons.isEmpty()) {
                Text(
                    text = stringRes(R.string.no_seasons),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            seasons.forEach { season ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSeasonDownload(season.seasonNumber) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = season.name?.takeIf { it.isNotBlank() }
                                ?: stringRes(R.string.season_number, season.seasonNumber),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringRes(
                                R.string.season_with_episodes,
                                episodeCounts[season.seasonNumber] ?: 0,
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringRes(R.string.action_download),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(R.string.action_cancel), fontSize = 13.sp)
            }
        }
    }
}
