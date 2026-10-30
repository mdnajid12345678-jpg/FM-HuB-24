package com.fmhub24.android.ui.screens.profile

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.PreferencesViewModel
import com.fmhub24.android.ui.components.SectionHeader
import com.fmhub24.android.ui.components.SelectChip
import com.fmhub24.android.ui.components.stringRes

/** Profile (spec 6.9): My list, History, Appearance, Playback, Download settings, Language, About. */
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
            icon = Icons.Outlined.List,
            title = stringRes(R.string.my_list_title),
            onClick = onOpenMyList,
        )
        ProfileRow(
            icon = Icons.Outlined.History,
            title = stringRes(R.string.history_title),
            onClick = onOpenHistory,
        )

        Spacer(Modifier.height(14.dp))
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

        Spacer(Modifier.height(14.dp))
        ProfileRow(
            icon = Icons.Outlined.PlayArrow,
            title = stringRes(R.string.playback_title),
            onClick = onOpenSettings,
        )
        ProfileRow(
            icon = Icons.Outlined.Download,
            title = stringRes(R.string.download_settings_title),
            onClick = onOpenSettings,
        )

        Spacer(Modifier.height(14.dp))
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

        Spacer(Modifier.height(14.dp))
        ProfileRow(
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about_title),
            onClick = onOpenAbout,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
