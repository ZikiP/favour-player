package com.ziki.favourplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun PlayerScreen(
    stream: ResolvedStream,
    contentKey: String,
    fromStart: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val playerPrefs = remember { PlayerPrefs(context).load() }
    val playbackStore = remember { PlaybackStore(context) }

    val player = remember(stream.masterUrl) { buildPlayer(context, stream) }
    var resumed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var suppressUntil by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedHeight by remember { mutableIntStateOf(-1) }

    fun saveProgress() {
        if (contentKey.isBlank()) return
        val duration = player.duration
        val position = player.currentPosition
        if (duration > 0L && position > 0L && player.playbackState != Player.STATE_ENDED) {
            playbackStore.save(contentKey, position, duration)
        }
    }

    fun maybeResume() {
        if (resumed || player.playbackState != Player.STATE_READY) return
        resumed = true
        val value = player.duration
        if (value > 0L) durationMs = value
        if (fromStart) {
            playbackStore.clear(contentKey)
        } else if (contentKey.isNotBlank()) {
            playbackStore.load(contentKey)?.let { saved ->
                Log.i("FavourPlayer", "resume at ${saved.positionMs}")
                player.seekTo(saved.positionMs)
            }
        }
    }

    DisposableEffect(player, contentKey) {
        onDispose {
            saveProgress()
            player.release()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.i("FavourPlayer", "isPlaying=$playing")
                isPlaying = playing
                if (!playing) saveProgress()
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.i("FavourPlayer", "playbackState=$state")
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    val value = player.duration
                    if (value > 0L) durationMs = value
                    maybeResume()
                }
                if (state == Player.STATE_ENDED) {
                    playbackStore.clear(contentKey)
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
                isBuffering = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, contentKey) {
        while (!resumed) {
            maybeResume()
            if (!resumed) delay(200)
        }
    }

    LaunchedEffect(player, contentKey) {
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

    LaunchedEffect(player, contentKey) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) saveProgress()
        }
    }

    LaunchedEffect(player) {
        player.setPlaybackSpeed(playerPrefs.defaultSpeed)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var previewMs by remember { mutableStateOf<Long?>(null) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(playerPrefs.defaultSpeed) }
    var locked by remember { mutableStateOf(false) }
    var showHints by remember { mutableStateOf(true) }

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
        showHints = false
    }

    fun seekTo(targetMs: Long) {
        val upper = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        val clamped = targetMs.coerceIn(0L, upper)
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

    LaunchedEffect(controlsVisible, interactionTick, isPlaying, locked) {
        if (controlsVisible && isPlaying && !locked) {
            delay(playerPrefs.autoHideMs.toLong())
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        delay(4_500)
        showHints = false
    }

    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }

    val spriteConfig = stream.sprite
    val spriteLoader = remember(stream.referer, stream.spriteUrlTemplate) {
        stream.spriteUrlTemplate?.let { SpriteLoader(stream.referer, it) }
    }
    val controlsBlockPx = with(LocalDensity.current) { 154.dp.toPx() }
    val subtitle = hostOf(stream.pageUrl)

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

        if (!locked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .playerGestures(
                        blockBottomPx = if (controlsVisible) controlsBlockPx else 0f,
                        onTap = {
                            controlsVisible = !controlsVisible
                            touch()
                        },
                        onDoubleTap = { isLeft ->
                            val delta = playerPrefs.doubleTapMs
                            seekTo(player.currentPosition + if (isLeft) -delta else delta)
                        },
                        onLongPressChange = { pressed ->
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
        }

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
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "播放失败",
                    color = PlayerColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "检查网络或页面是否仍可访问。",
                    color = PlayerColors.Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = message,
                    color = PlayerColors.Faint,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        errorMessage = null
                        isBuffering = true
                        player.prepare()
                        player.play()
                        controlsVisible = true
                    }) {
                        Text("重试", color = PlayerColors.Accent)
                    }
                    TextButton(onClick = onBack) {
                        Text("返回", color = PlayerColors.Muted)
                    }
                }
            }
        }

        hud?.let { current ->
            HudPill(current, Modifier.align(Alignment.Center))
        }

        if (showHints && !locked && errorMessage == null) {
            Text(
                text = "双击两侧 ±${playerPrefs.doubleTapMs / 1000}s · 长按 2×",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 168.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }

        val preview = previewMs
        val loader = spriteLoader
        if (spriteConfig != null && preview != null && loader != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 164.dp),
            ) {
                SpritePreview(
                    frame = frameAt(preview, durationMs, spriteConfig),
                    positionMs = preview,
                    config = spriteConfig,
                    loader = loader,
                )
            }
        }

        if (locked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.38f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .clickable {
                        locked = false
                        controlsVisible = true
                        touch()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = "解锁",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "已锁定",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                )
            }
        }

        if (controlsVisible && !locked && errorMessage == null) {
            PlayerControls(
                title = stream.title,
                subtitle = subtitle,
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
                onOpenOriginal = { openInBrowser(context, stream.pageUrl) },
                onLock = {
                    locked = true
                    controlsVisible = false
                },
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
                onSpeedSelected = { rate -> setSpeed(rate) },
                onQualitySelected = { option ->
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
                onSkipBack = { seekTo(player.currentPosition - 10_000L) },
                onSkipForward = { seekTo(player.currentPosition + 10_000L) },
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
    val upstream = OkHttpDataSource.Factory(OkHttpClient())
        .setDefaultRequestProperties(headers)
    val dataSourceFactory = MediaCache.dataSourceFactory(context, upstream)

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

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
