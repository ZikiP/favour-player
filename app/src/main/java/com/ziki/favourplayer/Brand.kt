package com.ziki.favourplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BrandMark(markSize: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(markSize)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(markSize * 0.28f),
                ambientColor = PlayerColors.Accent.copy(alpha = 0.35f),
                spotColor = PlayerColors.Accent.copy(alpha = 0.45f),
            )
            .clip(RoundedCornerShape(markSize * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF6F8BFF), Color(0xFF4056D6)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(markSize * 0.46f)) {
            val path = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.18f)
                lineTo(size.width * 0.70f, size.height * 0.18f)
                lineTo(size.width * 0.70f, size.height * 0.84f)
                lineTo(size.width * 0.50f, size.height * 0.68f)
                lineTo(size.width * 0.30f, size.height * 0.84f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
