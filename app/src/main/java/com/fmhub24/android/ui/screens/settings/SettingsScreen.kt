package com.fmhub24.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.DownloadDefaults
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.PreferencesViewModel
import com.fmhub24.android.ui.components.ThinProgress
import com.fmhub24.android.ui.components.formatBytes
import com.fmhub24.android.ui.components.stringRes
import com.fmhub24.android.ui.screens.downloads.DownloadsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    preferences: PreferencesViewModel = hiltViewModel(),
    downloads: DownloadsViewModel = hiltViewModel(),
) {
    val settings by preferences.settings.collectAsState(initial = null)
    val rows by downloads.rows.collectAsState()
    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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

        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            SettingsSectionLabel(stringRes(R.string.appearance_title))
            SegmentedSetting(
                options = listOf(
                    ThemeMode.SYSTEM.name to stringRes(R.string.theme_system),
                    ThemeMode.LIGHT.name to stringRes(R.string.theme_light),
                    ThemeMode.DARK.name to stringRes(R.string.theme_dark),
                ),
                selected = settings?.theme?.name,
                onSelect = { value ->
                    ThemeMode.entries.firstOrNull { it.name == value }?.let(preferences::chooseTheme)
                },
            )

            SettingsSectionLabel(stringRes(R.string.language_title))
            SegmentedSetting(
                options = listOf(
                    "" to stringRes(R.string.app_language_follow_system),
                    "en" to stringRes(R.string.language_english),
                    "bn" to stringRes(R.string.language_bengali),
                ),
                selected = settings?.languageTag ?: "",
                onSelect = preferences::chooseLanguage,
            )

            SettingsSectionLabel(stringRes(R.string.playback_title))
            Surface(shape = cardShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                    Text(
                        text = stringRes(R.string.preferred_audio),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Hindi", "Original", "Telugu", "Tamil", "Multi").forEach { language ->
                            SettingChoiceChip(
                                text = language,
                                selected = settings?.preferredAudio == language,
                                onClick = { preferences.setPreferredAudio(language) },
                            )
                        }
                    }
                }
            }

            SettingsSectionLabel(stringRes(R.string.download_settings_title))
            val used = rows.sumOf { it.sizeBytes ?: 0L }
            Surface(shape = cardShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringRes(R.string.settings_storage_used),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatBytes(used),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ThinProgress(
                        progress = downloads.storageUsageFraction(used),
                        modifier = Modifier.padding(horizontal = 16.dp).height(4.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    SettingsDivider()

                    SettingsToggleRow(
                        title = stringRes(R.string.wifi_only),
                        checked = settings?.wifiOnlyDownloads ?: true,
                        onCheckedChange = preferences::setWifiOnly,
                    )
                    SettingsDivider()

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = stringRes(R.string.default_quality),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            DownloadDefaults.QUALITY_OPTIONS.forEach { quality ->
                                SettingChoiceChip(
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
                    }
                    SettingsDivider()

                    SettingsStepperRow(
                        title = stringRes(R.string.simultaneous_downloads),
                        value = settings?.simultaneousDownloads ?: DownloadDefaults.DEFAULT_SIMULTANEOUS,
                        onChange = preferences::setSimultaneousDownloads,
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = stringRes(R.string.delete_after_watching),
                        checked = settings?.deleteAfterWatching ?: false,
                        onCheckedChange = preferences::setDeleteAfterWatching,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 9.dp),
    )
}

@Composable
private fun SegmentedSetting(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.fillMaxWidth().padding(5.dp)) {
            options.forEach { (value, label) ->
                val active = selected == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        thickness = 1.dp,
    )
}

@Composable
private fun SettingsToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
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
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color(0xFFB7C3BD),
                uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun SettingsStepperRow(title: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange((value - 1).coerceIn(1, 5)) }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Remove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = value.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = { onChange((value + 1).coerceIn(1, 5)) }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
