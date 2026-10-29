package com.fmhub24.android.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.fmhub24.android.R
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.theme.FMHuB24Theme
import kotlinx.coroutines.delay

private const val LANGUAGE_SYSTEM = ""
private const val LANGUAGE_ENGLISH = "en"
private const val LANGUAGE_BENGALI = "bn"

@Composable
fun FMHuB24Root(
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var introComplete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(2_600)
        introComplete = true
    }

    // Applies the stored preference after every launch and after each change.
    // An empty tag means "follow the system language".
    LaunchedEffect(settings?.languageTag) {
        val requested = settings?.languageTag ?: return@LaunchedEffect
        val target = if (requested.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(requested)
        }
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() != target.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    FMHuB24Theme(themeMode = settings?.theme ?: ThemeMode.DARK) {
        when {
            !introComplete || settings == null -> SplashScreen()
            settings?.languageTag == null -> LanguagePickerScreen(
                onContinue = { languageTag -> viewModel.chooseLanguage(languageTag) },
            )
            else -> HomeFoundationScreen(
                languageTag = settings?.languageTag ?: LANGUAGE_SYSTEM,
                themeMode = settings?.theme ?: ThemeMode.DARK,
                onLanguageSelected = viewModel::chooseLanguage,
                onThemeSelected = viewModel::chooseTheme,
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    val eightDp = with(LocalDensity.current) { 8.dp.roundToPx() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05052B), Color(0xFF0D0D3D)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FmHubLoader(Modifier.size(120.dp))
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(700, delayMillis = 400)) +
                    slideInVertically(tween(700, delayMillis = 400)) { eightDp },
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(650, delayMillis = 850)),
            ) {
                Text(
                    text = stringResource(R.string.tagline),
                    color = Color(0xFF3EA6FF),
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                )
            }
        }
    }
}

@Composable
private fun LanguagePickerScreen(onContinue: (String) -> Unit) {
    var selectedLanguage by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        listOf(
            LANGUAGE_BENGALI to stringResource(R.string.language_bengali),
            LANGUAGE_ENGLISH to stringResource(R.string.language_english),
        ).forEach { (tag, label) ->
            val selected = selectedLanguage == tag
            Card(
                onClick = { selectedLanguage = tag },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onContinue(selectedLanguage) },
            enabled = selectedLanguage.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.continue_button), fontSize = 14.sp)
        }
    }
}

@Composable
private fun HomeFoundationScreen(
    languageTag: String,
    themeMode: ThemeMode,
    onLanguageSelected: (String) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(28.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.catalog_not_connected),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.catalog_not_connected_detail),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        SettingsRow(
            title = stringResource(R.string.appearance_title),
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = themeMode,
            onSelect = onThemeSelected,
        )

        Spacer(Modifier.height(16.dp))
        SettingsRow(
            title = stringResource(R.string.language_title),
            options = listOf(
                LANGUAGE_SYSTEM to stringResource(R.string.app_language_follow_system),
                LANGUAGE_ENGLISH to stringResource(R.string.language_english),
                LANGUAGE_BENGALI to stringResource(R.string.language_bengali),
            ),
            selected = languageTag,
            onSelect = onLanguageSelected,
        )
    }
}

@Composable
private fun <T> SettingsRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (option, label) ->
            TextButton(
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected == option) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Text(text = label, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
