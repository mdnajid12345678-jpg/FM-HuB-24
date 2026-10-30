package com.fmhub24.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollState as rememberHScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.DownloadDefaults
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.PreferencesViewModel
import com.fmhub24.android.ui.components.SectionHeader
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.ThinProgress
import com.fmhub24.android.ui.components.formatBytes
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.downloads.DownloadsViewModel

/**
 * Settings (spec 6.8 and 6.9): storage bar, Wi-Fi only, default quality, preferred audio,
 * simultaneous downloads, delete after watching, plus appearance, language and playback.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    preferences: PreferencesViewModel = hiltViewModel(),
    downloads: DownloadsViewModel = hiltViewModel(),
) {
    val settings by preferences.settings.collectAsState(initial = null)
    val rows by downloads.rows.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
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
                text = stringRes(R.string.action_settings),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = stringRes(R.string.appearance_title))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.SYSTEM to stringRes(R.string.theme_system),
                    ThemeMode.LIGHT to stringRes(R.string.theme_light),
                    ThemeMode.DARK to stringRes(R.string.theme_dark),
                ).forEach { (mode, label) ->
                    SelectChip(
                        text = label,
                        selected = settings?.theme == mode,
                        onClick = { preferences.chooseTheme(mode) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = stringRes(R.string.language_title))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "" to stringRes(R.string.app_language_follow_system),
                    "en" to stringRes(R.string.language_english),
                    "bn" to stringRes(R.string.language_bengali),
                ).forEach { (tag, label) ->
                    SelectChip(
                        text = label,
                        selected = settings?.languageTag == tag,
                        onClick = { preferences.chooseLanguage(tag) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = stringRes(R.string.playback_title))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringRes(R.string.preferred_audio),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hindi", "Original", "Telugu", "Tamil", "Multi").forEach { language ->
                    SelectChip(
                        text = language,
                        selected = settings?.preferredAudio == language,
                        onClick = { preferences.setPreferredAudio(language) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringRes(R.string.player_no_subtitles),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    com.fmhub24.android.data.SubtitleSizes.SMALL to stringRes(R.string.text_size_small),
                    com.fmhub24.android.data.SubtitleSizes.MEDIUM to stringRes(R.string.text_size_medium),
                    com.fmhub24.android.data.SubtitleSizes.LARGE to stringRes(R.string.text_size_large),
                ).forEach { (size, label) ->
                    SelectChip(
                        text = label,
                        selected = settings?.subtitleSize == size,
                        onClick = { preferences.setSubtitleSize(size) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader(title = stringRes(R.string.download_settings_title))
            Spacer(Modifier.height(8.dp))

            // Storage bar (spec 6.8).
            val used = rows.sumOf { it.sizeBytes ?: 0L }
            Text(
                text = stringRes(R.string.downloads_storage_used, formatBytes(used)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ThinProgress(
                progress = if (used > 0) 0.35f else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
            )

            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = stringRes(R.string.wifi_only),
                checked = settings?.wifiOnlyDownloads ?: true,
                onCheckedChange = preferences::setWifiOnly,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringRes(R.string.default_quality),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScrollRow(),
            ) {
                DownloadDefaults.QUALITY_OPTIONS.forEach { quality ->
                    SelectChip(
                        text = if (quality == DownloadDefaults.ASK_EACH_TIME) {
                            stringRes(R.string.quality_ask)
                        } else {
                            "${quality}p"
                        },
                        selected = settings?.defaultQuality == quality,
                        onClick = { preferences.setDefaultQuality(quality) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringRes(R.string.preferred_audio),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScrollRow(),
            ) {
                listOf("Hindi", "Original", "Telugu", "Tamil", "Multi").forEach { language ->
                    SelectChip(
                        text = language,
                        selected = settings?.preferredAudio == language,
                        onClick = { preferences.setPreferredAudio(language) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            StepperRow(
                title = stringRes(R.string.simultaneous_downloads),
                value = settings?.simultaneousDownloads ?: DownloadDefaults.DEFAULT_SIMULTANEOUS,
                onChange = preferences::setSimultaneousDownloads,
            )

            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = stringRes(R.string.delete_after_watching),
                checked = settings?.deleteAfterWatching ?: false,
                onCheckedChange = preferences::setDeleteAfterWatching,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun StepperRow(title: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepButton(label = "−") { onChange((value - 1).coerceIn(1, 5)) }
        Text(
            text = value.toString(),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        StepButton(label = "+") { onChange((value + 1).coerceIn(1, 5)) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun Modifier.horizontalScrollRow(): Modifier =
    this.horizontalScroll(rememberHScroll())
