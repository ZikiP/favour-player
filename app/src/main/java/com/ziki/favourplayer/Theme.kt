package com.ziki.favourplayer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PlayerColors {
    val Bg = Color(0xFF07080B)
    val Panel = Color(0xFF111318)
    val PanelSoft = Color(0xFF181B22)
    val Border = Color(0xFF23262F)
    val BorderStrong = Color(0xFF333844)
    val Text = Color(0xFFF4F5F8)
    val Muted = Color(0xFF9CA3B4)
    val Faint = Color(0xFF6A7180)
    val Accent = Color(0xFFE7A54B)
    val AccentInk = Color(0xFF1A1207)
    val AccentSoft = Color(0xFF2A2115)
    val AccentLine = Color(0xFF4A3A20)
    val Danger = Color(0xFFFF6B6B)
    val Fav = Color(0xFFFF657D)
    val Success = Color(0xFF58D39C)
}

private val Palette = darkColorScheme(
    primary = PlayerColors.Accent,
    onPrimary = PlayerColors.AccentInk,
    secondary = PlayerColors.Accent,
    onSecondary = PlayerColors.AccentInk,
    background = PlayerColors.Bg,
    onBackground = PlayerColors.Text,
    surface = PlayerColors.Panel,
    onSurface = PlayerColors.Text,
    surfaceVariant = PlayerColors.PanelSoft,
    onSurfaceVariant = PlayerColors.Muted,
    outline = PlayerColors.Border,
    outlineVariant = PlayerColors.BorderStrong,
    error = PlayerColors.Danger,
)

private val Type = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val FavourShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun FavourPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Palette,
        typography = Type,
        shapes = FavourShapes,
        content = content,
    )
}
