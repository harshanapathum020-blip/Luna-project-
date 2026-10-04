package com.luna.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LunaDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    background = DarkSurface,
    surface = DarkSurfaceContainer,
    onPrimary = DarkSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun LunaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LunaDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
