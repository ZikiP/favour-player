package com.ziki.favourplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(stream: ResolvedStream, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    val player = remember(stream.masterUrl) { buildPlayer(context, stream) }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var suppressUntil by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedHeight by remember { mutableIntStateOf(-1) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.i("FavourPlayer", "isPlaying=$playing")
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.i("FavourPlayer", "playbackState=$state")
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    val value = player.duration
                    if (value > 0L) durationMs = value
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                qualities = buildQualityOptions(player)
                Log.i("FavourPlayer", "tracks=${qualities.joinToString { it.label }}")
                val value = player.duration
                if (value > 0L) durationMs = value
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("FavourPlayer", "player error ${error.errorCodeName}", error)
                errorMessage = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        Log.i("FavourPlayer", "player created: ${stream.masterUrl}")
        var lastPos = 0L
        while (true) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
            if (pos < lastPos - 400L) {
                Log.w(
                    "FavourPlayer",
                    "backjump $lastPos -> $pos state=${player.playbackState}",
                )
            }
            lastPos = pos
            if (SystemClock.elapsedRealtime() >= suppressUntil) {
                positionMs = pos
            }
            delay(250)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var previewMs by remember { mutableStateOf<Long?>(null) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var brightness by remember {
        mutableFloatStateOf(
            activity.window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f,
        )
    }

    fun touch() {
        interactionTick++
    }

    fun seekTo(targetMs: Long) {
        val upper = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        val clamped = targetMs.coerceIn(0L, upper)
        Log.i("FavourPlayer", "seekTo=$clamped")
        player.seekTo(clamped)
        positionMs = clamped
        suppressUntil = SystemClock.elapsedRealtime() + 700L
        touch()
    }

    fun setSpeed(rate: Float) {
        playbackSpeed = rate
        player.setPlaybackSpeed(rate)
        touch()
    }

    fun applyBrightness(delta: Float) {
        brightness = (brightness + delta).coerceIn(0.02f, 1f)
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = brightness
        }
        hud = Hud.Level("亮度", (brightness * 100).roundToInt())
        touch()
    }

    fun applyVolume(delta: Float) {
        val next = (volume + delta * maxVolume).roundToInt().coerceIn(0, maxVolume)
        if (next != volume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
            volume = next
        }
        hud = Hud.Level("音量", (next * 100f / maxVolume).roundToInt())
        touch()
    }

    LaunchedEffect(controlsVisible, interactionTick, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            Log.i("FavourPlayer", "controls auto-hide")
            controlsVisible = false
        }
    }

    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }

    val spriteConfig = stream.sprite
    val spriteLoader = remember(stream.referer, stream.uuid) {
        SpriteLoader(stream.referer, stream.uuid)
    }
    val controlsBlockPx = with(LocalDensity.current) { 190.dp.toPx() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                val attached = player
                PlayerView(viewContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepScreenOn(true)
                    this.player = attached
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(
                    blockBottomPx = if (controlsVisible) controlsBlockPx else 0f,
                    onTap = {
                        controlsVisible = !controlsVisible
                        Log.i("FavourPlayer", "tap controlsVisible=$controlsVisible")
                        touch()
                    },
                    onDoubleTap = { isLeft ->
                        Log.i("FavourPlayer", "doubleTap left=$isLeft")
                        seekTo(player.currentPosition + if (isLeft) -10_000L else 10_000L)
                    },
                    onLongPressChange = { pressed ->
                        Log.i("FavourPlayer", "longPress=$pressed")
                        if (pressed) {
                            player.setPlaybackSpeed(2f)
                            hud = Hud.Speed(2f)
                        } else {
                            player.setPlaybackSpeed(playbackSpeed)
                            hud = null
                        }
                    },
                    onSeekBegin = {
                        seekBaseMs = player.currentPosition
                        previewMs = seekBaseMs
                    },
                    onSeekFraction = { fraction ->
                        val target = (seekBaseMs + (fraction * 120_000f).toLong())
                            .coerceIn(0L, durationMs.coerceAtLeast(0L))
                        previewMs = target
                        hud = Hud.Seek(target)
                        touch()
                    },
                    onSeekEnd = {
                        previewMs?.let { seekTo(it) }
                        previewMs = null
                    },
                    onVerticalDelta = { isLeft, delta ->
                        if (isLeft) applyBrightness(delta) else applyVolume(delta)
                    },
                ),
        )

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PlayerColors.Accent,
            )
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "播放失败：$message", color = PlayerColors.Text)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("返回", color = PlayerColors.Accent) }
            }
        }

        hud?.let { current ->
            HudPill(current, Modifier.align(Alignment.Center))
        }

        val preview = previewMs
        if (spriteConfig != null && preview != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 210.dp),
            ) {
                SpritePreview(
                    frame = frameAt(preview, durationMs, spriteConfig),
                    positionMs = preview,
                    config = spriteConfig,
                    loader = spriteLoader,
                )
            }
        }

        if (controlsVisible && errorMessage == null) {
            PlayerControls(
                title = stream.title,
                isPlaying = isPlaying,
                positionMs = previewMs ?: positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                speed = playbackSpeed,
                qualities = qualities,
                selectedHeight = selectedHeight,
                onTogglePlay = {
                    if (player.isPlaying) player.pause() else player.play()
                    touch()
                },
                onBack = onBack,
                onSeekStart = {
                    previewMs = player.currentPosition
                    touch()
                },
                onSeek = { target ->
                    previewMs = target
                    hud = Hud.Seek(target)
                    touch()
                },
                onSeekEnd = { target ->
                    previewMs = null
                    seekTo(target)
                },
                onSpeedSelected = { rate ->
                    Log.i("FavourPlayer", "speed=$rate")
                    setSpeed(rate)
                },
                onQualitySelected = { option ->
                    Log.i("FavourPlayer", "quality=${option?.label ?: "自动"}")
                    applyQuality(player, option)
                    selectedHeight = option?.height ?: -1
                    touch()
                },
                onRotate = {
                    val landscape = activity.resources.configuration.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
                    activity.requestedOrientation = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    touch()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun buildPlayer(context: Context, stream: ResolvedStream): ExoPlayer {
    val headers = buildMap {
        put("User-Agent", Resolver.DESKTOP_UA)
        if (stream.referer.isNotBlank()) put("Referer", stream.referer)
    }
    val dataSourceFactory = OkHttpDataSource.Factory(OkHttpClient())
        .setDefaultRequestProperties(headers)

    val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(MediaItem.fromUri(stream.masterUrl))

    return ExoPlayer.Builder(context)
        .build()
        .apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
}

private fun frameAt(positionMs: Long, durationMs: Long, config: SpriteConfig): Int {
    if (durationMs <= 0L) return 0
    val ratio = positionMs.toDouble() / durationMs.toDouble()
    return (ratio * config.frameCount).toInt().coerceIn(0, config.frameCount - 1)
}
