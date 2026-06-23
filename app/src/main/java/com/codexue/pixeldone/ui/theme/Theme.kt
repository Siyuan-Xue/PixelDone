package com.codexue.pixeldone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PixelDoneColorScheme = lightColorScheme(
    primary = ClaudeClay,
    onPrimary = ClaudeSlateDark,
    primaryContainer = ClaudeIvoryDark,
    onPrimaryContainer = ClaudeSlateDark,
    secondary = ClaudeMineral,
    onSecondary = ClaudeSlateDark,
    tertiary = ClaudeSky,
    onTertiary = ClaudeSlateDark,
    background = ClaudeIvory,
    onBackground = ClaudeSlateDark,
    surface = ClaudeGray100,
    onSurface = ClaudeSlateDark,
    surfaceVariant = ClaudeOat,
    onSurfaceVariant = ClaudeGray700,
    outline = ClaudeGray300,
    outlineVariant = ClaudeIvoryDark,
    error = PixelError,
    onError = ClaudeSlateDark,
    errorContainer = ClaudeCoral,
    onErrorContainer = ClaudeSlateDark,
)

@Composable
fun PixelDoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PixelDoneColorScheme,
        typography = Typography,
        content = content,
    )
}
