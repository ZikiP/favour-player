package com.ziki.favourplayer

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride

data class QualityOption(
    val height: Int,
    val label: String,
    val override: TrackSelectionOverride,
)

fun buildQualityOptions(player: Player): List<QualityOption> {
    val options = mutableListOf<QualityOption>()
    for (group in player.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (index in 0 until group.length) {
            val format = group.getTrackFormat(index)
            val height = format.height
            if (height <= 0 || options.any { it.height == height }) continue
            options += QualityOption(
                height = height,
                label = "${height}p",
                override = TrackSelectionOverride(group.mediaTrackGroup, index),
            )
        }
    }
    return options.sortedByDescending { it.height }
}

/** 当前实际在播的视频轨分辨率；拿不到就退回解码器给的画面高度。 */
fun currentVideoHeight(player: Player): Int {
    for (group in player.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO || !group.isSelected) continue
        for (index in 0 until group.length) {
            if (!group.isTrackSelected(index)) continue
            val height = group.getTrackFormat(index).height
            if (height > 0) return height
        }
    }
    return player.videoSize.height
}

fun applyQuality(player: Player, option: QualityOption?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        player.trackSelectionParameters = builder
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMinVideoSize(0, 0)
            .build()
    } else {
        player.trackSelectionParameters = builder
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setOverrideForType(option.override)
            .build()
    }
}
