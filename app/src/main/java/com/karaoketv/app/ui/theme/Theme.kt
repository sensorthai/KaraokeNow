package com.karaoketv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.lightColorScheme

object KaraokeColors {
    val Background = Color(0xFFEEF1F5)
    val Surface = Color(0xFFF6F8FB)
    val SurfaceElevated = Color(0xFFE6EAF0)
    val Primary = Color(0xFFF59E0B)
    val Secondary = Color(0xFFFF8C00)
    val TextPrimary = Color(0xFF1E293B)
    val TextSecondary = Color(0xFF64748B)
}

@OptIn(ExperimentalTvMaterial3Api::class)
private val KaraokeLightColorScheme = lightColorScheme(
    primary = KaraokeColors.Primary,
    onPrimary = Color.White,
    secondary = KaraokeColors.Secondary,
    onSecondary = Color.White,
    background = KaraokeColors.Background,
    onBackground = KaraokeColors.TextPrimary,
    surface = KaraokeColors.Surface,
    onSurface = KaraokeColors.TextPrimary,
    surfaceVariant = KaraokeColors.SurfaceElevated,
    onSurfaceVariant = KaraokeColors.TextSecondary
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KaraokeTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaraokeLightColorScheme,
        content = content
    )
}
