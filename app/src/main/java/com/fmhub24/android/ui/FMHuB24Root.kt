package com.fmhub24.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.fmhub24.android.R
import com.fmhub24.android.data.ThemeMode
import com.fmhub24.android.ui.navigation.FmHubApp
import com.fmhub24.android.ui.theme.FMHuB24Theme

private const val LANGUAGE_SYSTEM = ""
private const val LANGUAGE_ENGLISH = "en"
private const val LANGUAGE_BENGALI = "bn"

/**
 * App root: splash, first-run language picker, the notification permission explainer before the
 * system prompt (spec 6.1), then the five-tab shell.
 */
@Composable
fun FMHuB24Root(
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var showNotificationRationale by rememberSaveable { mutableStateOf(false) }

    // Applies the stored preference after every launch and after each change.
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
            settings == null -> SplashScreen()
            settings?.languageTag == null -> LanguagePickerScreen(
                onContinue = { languageTag -> viewModel.chooseLanguage(languageTag) },
            )
            else -> {
                val context = LocalContext.current
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted -> viewModel.markNotificationRationaleSeen() }

                // Explainer before the system prompt (spec 6.1).
                LaunchedEffect(Unit) {
                    if (settings?.notificationRationaleShown == false) {
                        showNotificationRationale = true
                    }
                }

                if (showNotificationRationale) {
                    NotificationRationaleDialog(
                        onNotNow = {
                            showNotificationRationale = false
                            viewModel.markNotificationRationaleSeen()
                        },
                        onAllow = {
                            showNotificationRationale = false
                            viewModel.markNotificationRationaleSeen()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                context.checkSelfPermission(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }

                val navController = rememberNavController()
                FmHubApp(navController = navController)
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val eightDp = with(androidx.compose.ui.platform.LocalDensity.current) {
        8.dp.roundToPx()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF08130F), Color(0xFF14221D)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FmHubLoader(Modifier.size(120.dp))
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(600, delayMillis = 1200)) +
                    slideInVertically(tween(600, delayMillis = 1200)) { eightDp },
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(500, delayMillis = 1700)),
            ) {
                Text(
                    text = stringResource(R.string.tagline),
                    color = Color(0xFFF4B84A),
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FmHubLoader(Modifier.size(60.dp))
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))

        listOf(
            LANGUAGE_BENGALI to stringResource(R.string.language_bengali),
            LANGUAGE_ENGLISH to stringResource(R.string.language_english),
        ).forEach { (tag, label) ->
            val selected = selectedLanguage == tag
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { selectedLanguage = tag }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 15.sp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onContinue(selectedLanguage) },
            enabled = selectedLanguage.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(stringResource(R.string.continue_button), fontSize = 14.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.language_hint_later),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Explainer shown before Android's own notification permission prompt (spec 6.1). */
@Composable
private fun NotificationRationaleDialog(
    onNotNow: () -> Unit,
    onAllow: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.notification_rationale_title)) },
        text = { Text(stringResource(R.string.notification_rationale_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}
