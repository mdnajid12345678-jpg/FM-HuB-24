package com.fmhub24.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.PreferencesViewModel
import com.fmhub24.android.ui.components.SectionHeader
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.stringRes

/** Profile (spec 6.9) laid out exactly like the design mockup: rows on the elevated surface. */
@Composable
fun ProfileScreen(
    onOpenMyList: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    preferences: PreferencesViewModel = hiltViewModel(),
) {
    val settings by preferences.settings.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringRes(R.string.tab_profile),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        ProfileRow(
            title = stringRes(R.string.my_list_title),
            onClick = onOpenMyList,
        )
        ProfileRow(
            title = stringRes(R.string.history_title),
            onClick = onOpenHistory,
        )

        SectionCaption(stringRes(R.string.appearance_title))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.settings_theme),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            }
        }

        SectionCaption(stringRes(R.string.playback_title))
        ProfileRow(
            title = stringRes(R.string.preferred_audio),
            value = settings?.preferredAudio ?: stringRes(R.string.auto_option),
            onClick = onOpenSettings,
        )
        ProfileRow(
            title = stringRes(R.string.download_settings_title),
            onClick = onOpenSettings,
        )
        ProfileRow(
            title = stringRes(R.string.language_title),
            value = languageLabel(settings?.languageTag),
            onClick = onOpenSettings,
        )

        SectionCaption(stringRes(R.string.about_title))
        ProfileRow(
            title = stringRes(R.string.about_title),
            onClick = onOpenAbout,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SectionCaption(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        fontSize = 11.sp,
        color = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun languageLabel(tag: String?): String = when (tag) {
    "en" -> stringRes(R.string.language_english)
    "bn" -> stringRes(R.string.language_bengali)
    else -> stringRes(R.string.app_language_follow_system)
}

@Composable
private fun ProfileRow(title: String, onClick: () -> Unit, value: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
    }
}
