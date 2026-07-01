package com.milesxue.pixeldone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PixelDonePalette(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val surfaceRaised: Color,
    val selectedSurface: Color,
    val completedSurface: Color,
    val destructiveSurface: Color,
    val border: Color,
    val borderWeak: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val primaryInteractive: Color,
    val success: Color,
    val error: Color,
    val disabledSurface: Color,
    val disabledText: Color,
    val onPrimary: Color,
)

private val LightPixelDonePalette = PixelDonePalette(
    background = ClaudeIvory,
    surface = ClaudeGray100,
    surfaceSoft = ClaudeIvory,
    surfaceRaised = ClaudeIvoryMedium,
    selectedSurface = ClaudeOat,
    completedSurface = ClaudeCactus.copy(alpha = 0.35f),
    destructiveSurface = ClaudeCoral,
    border = ClaudeGray600,
    borderWeak = ClaudeGray300,
    textPrimary = ClaudeSlateDark,
    textSecondary = ClaudeSlateLight,
    primary = ClaudeClay,
    primaryInteractive = ClaudeClayInteractive,
    success = ClaudeMineral,
    error = PixelError,
    disabledSurface = ClaudeGray200,
    disabledText = ClaudeGray600,
    onPrimary = ClaudeSlateDark,
)

private val DarkPixelDonePalette = PixelDonePalette(
    background = ClaudeGray950,
    surface = ClaudeGray850,
    surfaceSoft = ClaudeGray800,
    surfaceRaised = ClaudeGray750,
    selectedSurface = ClaudeGray700,
    completedSurface = ClaudeMineral.copy(alpha = 0.22f),
    destructiveSurface = Color(0xFF3A2424),
    border = ClaudeGray600,
    borderWeak = ClaudeGray700,
    textPrimary = ClaudeGray050,
    textSecondary = ClaudeGray400,
    primary = ClaudeClay,
    primaryInteractive = ClaudeClayInteractive,
    success = ClaudeMineral,
    error = PixelError,
    disabledSurface = ClaudeGray800,
    disabledText = ClaudeGray600,
    onPrimary = ClaudeGray050,
)

private val LocalPixelDonePalette = staticCompositionLocalOf { LightPixelDonePalette }

object PixelDoneColors {
    val current: PixelDonePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPixelDonePalette.current
}

private val PixelDoneLightColorScheme = lightColorScheme(
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

private val PixelDoneDarkColorScheme = darkColorScheme(
    primary = ClaudeClay,
    onPrimary = ClaudeSlateDark,
    primaryContainer = ClaudeGray750,
    onPrimaryContainer = ClaudeGray050,
    secondary = ClaudeMineral,
    onSecondary = ClaudeSlateDark,
    tertiary = ClaudeSky,
    onTertiary = ClaudeSlateDark,
    background = ClaudeGray950,
    onBackground = ClaudeGray050,
    surface = ClaudeGray850,
    onSurface = ClaudeGray050,
    surfaceVariant = ClaudeGray750,
    onSurfaceVariant = ClaudeGray400,
    outline = ClaudeGray600,
    outlineVariant = ClaudeGray700,
    error = PixelError,
    onError = ClaudeSlateDark,
    errorContainer = Color(0xFF3A2424),
    onErrorContainer = ClaudeGray050,
)

@Composable
fun PixelDoneTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPixelDonePalette provides if (darkTheme) DarkPixelDonePalette else LightPixelDonePalette,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) PixelDoneDarkColorScheme else PixelDoneLightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
