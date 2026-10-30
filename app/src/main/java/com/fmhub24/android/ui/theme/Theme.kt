package com.fmhub24.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fmhub24.android.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkPrimaryText,
    primaryContainer = Color(0xFF4C3916),
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkCyan,
    onSecondary = DarkBackground,
    tertiary = DarkViolet,
    background = DarkBackground,
    onBackground = Color.White,
    surface = DarkSurface,
    onSurface = Color.White,
    surfaceVariant = DarkElevated,
    onSurfaceVariant = Color(0xFFADADC9),
    outline = Color.White.copy(alpha = 0.12f),
    error = DarkDanger,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightInk,
    secondary = LightCyan,
    onSecondary = Color.White,
    tertiary = LightViolet,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightInk.copy(alpha = 0.68f),
    outline = LightInk.copy(alpha = 0.12f),
    error = LightDanger,
)

@Composable
fun FMHuB24Theme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content,
    )
}
