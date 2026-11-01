package com.ziki.favourplayer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object PlayerColors {
    val Bg = Color(0xFF0E1014)
    val Panel = Color(0xF2161921)
    val PanelSoft = Color(0xFF1B1F29)
    val Border = Color(0xFF262B36)
    val BorderStrong = Color(0xFF333A48)
    val Text = Color(0xFFE8EAF0)
    val Muted = Color(0xFF98A2B3)
    val Faint = Color(0xFF6B7486)
    val Accent = Color(0xFF7B93FF)
    val AccentInk = Color(0xFF0E1014)
    val AccentSoft = Color(0xFF1C2340)
    val AccentLine = Color(0xFF2F3B6B)
    val Danger = Color(0xFFFF7B72)
    val Fav = Color(0xFFFF5D73)
}

private val Palette = darkColorScheme(
    primary = PlayerColors.Accent,
    onPrimary = PlayerColors.AccentInk,
    secondary = PlayerColors.Accent,
    background = PlayerColors.Bg,
    onBackground = PlayerColors.Text,
    surface = PlayerColors.Panel,
    onSurface = PlayerColors.Text,
    surfaceVariant = PlayerColors.PanelSoft,
    onSurfaceVariant = PlayerColors.Muted,
    outline = PlayerColors.Border,
    error = PlayerColors.Danger,
)

@Composable
fun FavourPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
