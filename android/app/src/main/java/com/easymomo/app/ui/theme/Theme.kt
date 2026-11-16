package com.easymomo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val EasyMoMoLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightEasyMoMoPalette.emeraldPrimaryContainer,
    onPrimaryContainer = LightEasyMoMoPalette.onEmeraldPrimaryContainer,
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = LightEasyMoMoPalette.amberContainer,
    onSecondaryContainer = LightEasyMoMoPalette.onAmberContainer,
    background = LightEasyMoMoPalette.terminalBackground,
    onBackground = LightEasyMoMoPalette.textPrimary,
    surface = LightEasyMoMoPalette.terminalSurface,
    onSurface = LightEasyMoMoPalette.textPrimary,
    surfaceVariant = LightEasyMoMoPalette.terminalSurfaceVariant,
    onSurfaceVariant = LightEasyMoMoPalette.textSecondary,
    outline = LightEasyMoMoPalette.terminalBorder,
    error = LightEasyMoMoPalette.statusError,
    onError = Color.White,
    errorContainer = LightEasyMoMoPalette.statusErrorBg,
    onErrorContainer = LightEasyMoMoPalette.statusError
)

private val EasyMoMoDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkEasyMoMoPalette.emeraldPrimaryContainer,
    onPrimaryContainer = DarkEasyMoMoPalette.onEmeraldPrimaryContainer,
    secondary = AmberAccent,
    onSecondary = Color.White,
    secondaryContainer = DarkEasyMoMoPalette.amberContainer,
    onSecondaryContainer = DarkEasyMoMoPalette.onAmberContainer,
    background = DarkEasyMoMoPalette.terminalBackground,
    onBackground = DarkEasyMoMoPalette.textPrimary,
    surface = DarkEasyMoMoPalette.terminalSurface,
    onSurface = DarkEasyMoMoPalette.textPrimary,
    surfaceVariant = DarkEasyMoMoPalette.terminalSurfaceVariant,
    onSurfaceVariant = DarkEasyMoMoPalette.textSecondary,
    outline = DarkEasyMoMoPalette.terminalBorder,
    error = DarkEasyMoMoPalette.statusError,
    onError = Color.White,
    errorContainer = DarkEasyMoMoPalette.statusErrorBg,
    onErrorContainer = DarkEasyMoMoPalette.statusError
)

@Composable
fun EasyMoMoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkEasyMoMoPalette else LightEasyMoMoPalette
    val colorScheme = if (darkTheme) EasyMoMoDarkColorScheme else EasyMoMoLightColorScheme

    CompositionLocalProvider(LocalEasyMoMoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EasyMoMoTypography,
            content = content
        )
    }
}
