package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface Hud {
    data class Seek(val targetMs: Long) : Hud
    data class Level(val label: String, val percent: Int) : Hud
    data class Speed(val rate: Float) : Hud
}

@Composable
fun HudPill(hud: Hud, modifier: Modifier = Modifier) {
    val text = when (hud) {
        is Hud.Seek -> "跳转 ${formatTime(hud.targetMs)}"
        is Hud.Level -> "${hud.label} ${hud.percent}%"
        is Hud.Speed -> "倍速 ${formatSpeed(hud.rate)}"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = text, color = PlayerColors.Text, fontSize = 15.sp)
    }
}
