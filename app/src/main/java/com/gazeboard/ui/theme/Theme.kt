package com.gazeboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color.White,
    background = GlassColors.Background,
    surface = GlassColors.Surface,
    onBackground = GlassColors.TextPrimary,
    onSurface = GlassColors.TextPrimary,
)

@Composable
fun GazeBoardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
