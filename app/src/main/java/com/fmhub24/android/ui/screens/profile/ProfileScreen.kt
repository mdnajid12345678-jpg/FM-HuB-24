package com.fmhub24.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.PreferencesViewModel
import com.fmhub24.android.ui.components.stringRes

@Composable
fun ProfileScreen(
    onOpenMyList: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    preferences: PreferencesViewModel = hiltViewModel(),
) {
    val settings by preferences.settings.collectAsState(initial = null)
    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringRes(R.string.tab_profile),
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().clip(cardShape).clickable(onClick = onOpenSettings),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(58.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PersonOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringRes(R.string.profile_space_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringRes(R.string.profile_space_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.BookmarkBorder,
                title = stringRes(R.string.my_list_title),
                onClick = onOpenMyList,
            )
            ShortcutCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.History,
                title = stringRes(R.string.history_title),
                onClick = onOpenHistory,
            )
        }

        SectionCaption(stringRes(R.string.appearance_title))
        SegmentedThemeControl(
            selected = settings?.theme,
            onSelect = preferences::chooseTheme,
        )

        SectionCaption(stringRes(R.string.playback_title))
        Surface(shape = cardShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column {
                ProfileActionRow(
                    icon = Icons.Outlined.VolumeUp,
                    title = stringRes(R.string.preferred_audio),
                    value = settings?.preferredAudio ?: stringRes(R.string.auto_option),
                    onClick = onOpenSettings,
                )
                ProfileDivider()
                ProfileActionRow(
                    icon = Icons.Outlined.Download,
                    title = stringRes(R.string.download_settings_title),
                    onClick = onOpenSettings,
                )
                ProfileDivider()
                ProfileActionRow(
                    icon = Icons.Outlined.Language,
                    title = stringRes(R.string.language_title),
                    value = languageLabel(settings?.languageTag),
                    onClick = onOpenSettings,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            ProfileActionRow(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about_title),
                onClick = onOpenAbout,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ShortcutCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
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
private fun SegmentedThemeControl(
    selected: ThemeMode?,
    onSelect: (ThemeMode) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.fillMaxWidth().padding(5.dp)) {
            listOf(
                ThemeMode.SYSTEM to stringRes(R.string.theme_system),
                ThemeMode.LIGHT to stringRes(R.string.theme_light),
                ThemeMode.DARK to stringRes(R.string.theme_dark),
            ).forEach { (mode, label) ->
                val active = selected == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        thickness = 1.dp,
    )
}

@Composable
private fun languageLabel(tag: String?): String = when (tag) {
    "en" -> stringRes(R.string.language_english)
    "bn" -> stringRes(R.string.language_bengali)
    else -> stringRes(R.string.app_language_follow_system)
}
