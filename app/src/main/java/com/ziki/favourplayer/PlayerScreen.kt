package com.ziki.favourplayer

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val subtitleStore = remember { SubtitleStore(context) }
    val subtitleStyleStore = remember { SubtitleStyleStore(context) }
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf<SubtitleTrack?>(null) }
    // 控件位置可自由调，字幕避让要防止超出画面
    // 控件位置可自由调，竖横屏各存一份
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val controlBottomDp = if (isLandscape) {
        playerPrefs.controlBottomLandscape
    } else {
        playerPrefs.controlBottomPortrait
    }
    var subtitleStyle by remember { mutableStateOf(subtitleStyleStore.load(contentKey)) }
    var showSubtitleSettings by remember { mutableStateOf(false) }

    // —— 字幕跨设备共享（按作品）——
    // 干净模式跳过上传：避免把看过哪些片暴露到服务端。

    fun uploadSubtitle(name: String, text: String) {
        if (contentKey.isBlank() || CleanMode.enabled) return
        val config = SyncStore(context).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) return
        val style = subtitleStyle
        scope.launch(Dispatchers.IO) {
            SubtitleSync.upload(config, contentKey, name, text, style)
        }
    }

    // 只调样式时，正文带本地已有的（没有就留空，服务端保留旧正文）。
    fun uploadSubtitleStyle() {
        if (contentKey.isBlank() || CleanMode.enabled) return
        val config = SyncStore(context).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) return
        val style = subtitleStyle
        scope.launch(Dispatchers.IO) {
            val local = subtitleStore.loadText(contentKey)
            SubtitleSync.upload(
                config, contentKey,
                local?.first.orEmpty(), local?.second.orEmpty(), style,
            )
        }
    }

    fun deleteSharedSubtitle() {
        if (contentKey.isBlank() || CleanMode.enabled) return
        val config = SyncStore(context).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) return
        scope.launch(Dispatchers.IO) { SubtitleSync.delete(config, contentKey) }
    }

    // 先加载本地已有字幕（私有副本，其次旧 URI 记录），离线也能秒显。
    suspend fun loadLocalSubtitle() {
        val cached = subtitleStore.loadText(contentKey)
        if (cached != null) {
            val cues = withContext(Dispatchers.IO) { SubtitleParser.parse(cached.second) }
            if (cues.isNotEmpty()) {
                caption = SubtitleTrack(cached.first, cues)
                return
            }
        }
        val saved = subtitleStore.load(contentKey) ?: return
        val (uri, name) = saved
        val raw = withContext(Dispatchers.IO) {
            runCatching { SubtitleLoader.read(context, uri) }.getOrNull()
        } ?: return
        val cues = withContext(Dispatchers.IO) { SubtitleParser.parse(raw) }
        if (cues.isEmpty()) {
            subtitleStore.clear(contentKey)
        } else {
            caption = SubtitleTrack(name, cues)
            subtitleStore.saveText(contentKey, name, raw)
        }
    }

    fun applySubtitleFile(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SubtitleLoader.takePermission(context, uri)
                    val name = SubtitleLoader.displayName(context, uri)
                    val raw = SubtitleLoader.read(context, uri)
                    Triple(name, SubtitleParser.parse(raw), raw)
                }
            }
            result.onSuccess { (name, cues, raw) ->
                if (cues.isEmpty()) {
                    Toast.makeText(
                        context,
                        "没解析到字幕条目，支持 SRT / VTT / ASS",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@onSuccess
                }
                caption = SubtitleTrack(name, cues)
                if (contentKey.isNotBlank()) {
                    subtitleStore.save(contentKey, uri, name)
                    // 存一份到私有目录，之后不再依赖外部 URI 的授权
                    subtitleStore.saveText(contentKey, name, raw)
                }
                // 挂载后把正文和当前样式共享到中间服务，别的设备打开能自动加载
                uploadSubtitle(name, raw)
                Toast.makeText(context, "已加载 $name", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Log.e("FavourPlayer", "subtitle failed", it)
                // 有些异常（尤其 SecurityException）message 为空，必须把类名带出来
                val detail = it.message?.take(80)
                val reason = if (detail.isNullOrBlank()) {
                    it.javaClass.simpleName
                } else {
                    "${it.javaClass.simpleName}：$detail"
                }
                Toast.makeText(context, "字幕加载失败 · $reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) applySubtitleFile(uri) }

    val player = remember(stream.masterUrl) {
        buildPlayer(context, stream, playerPrefs.sharpen)
    }
    var resumed by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var suppressUntil by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var errorReport by remember { mutableStateOf<PlaybackDiagnostics.Report?>(null) }
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedHeight by remember { mutableIntStateOf(-1) }
    // 视频画面比例，用来把字幕贴到画面底边而不是屏幕底边
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }

    fun saveProgress() {
        if (contentKey.isBlank()) return
        // 干净模式不留下任何观看痕迹
        if (CleanMode.enabled) return
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
        if (CleanMode.enabled) return
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

    // 帧率匹配：把显示刷新率调到片源帧率的整数倍，消除 24p 在 60Hz 上的 3:2 抖动
    DisposableEffect(player, playerPrefs.frameRateMatch) {
        if (!playerPrefs.frameRateMatch) return@DisposableEffect onDispose { }
        var appliedFps = 0f
        fun apply(videoFps: Float) {
            if (videoFps <= 1f || videoFps == appliedFps) return
            if (applyFrameRateMatch(activity, videoFps)) appliedFps = videoFps
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                apply(player.videoFormat?.frameRate ?: -1f)
            }

            override fun onTracksChanged(tracks: Tracks) {
                apply(player.videoFormat?.frameRate ?: -1f)
            }
        }
        player.addListener(listener)
        apply(player.videoFormat?.frameRate ?: -1f)
        onDispose {
            player.removeListener(listener)
            restoreDefaultDisplayMode(activity)
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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val rotated = videoSize.unappliedRotationDegrees % 180 != 0
                val width = if (rotated) videoSize.height else videoSize.width
                val height = if (rotated) videoSize.width else videoSize.height
                if (width > 0 && height > 0) videoAspect = width.toFloat() / height
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("FavourPlayer", "player error ${error.errorCodeName}", error)
                val report = PlaybackDiagnostics.describe(error, stream.masterUrl)
                errorReport = report
                errorMessage = report.summary
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

    LaunchedEffect(contentKey) {
        if (contentKey.isBlank()) return@LaunchedEffect

        // 先读本地已有字幕：离线/无网也能立即显示，不依赖中间服务
        loadLocalSubtitle()

        // 再拉共享字幕：远端优先，拉到就覆盖本地（正文 + 整份样式）
        val config = SyncStore(context).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) return@LaunchedEffect
        if (CleanMode.enabled) return@LaunchedEffect
        val remote = withContext(Dispatchers.IO) { SubtitleSync.download(config, contentKey) }
            ?: return@LaunchedEffect
        if (remote.text.isNotBlank()) {
            val cues = withContext(Dispatchers.IO) { SubtitleParser.parse(remote.text) }
            if (cues.isNotEmpty()) {
                caption = SubtitleTrack(remote.name.ifBlank { "共享字幕" }, cues)
                subtitleStore.saveText(contentKey, remote.name, remote.text)
            }
        }
        remote.style?.let { style ->
            subtitleStyle = style
            subtitleStyleStore.save(contentKey, style)
        }
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

    LaunchedEffect(controlsVisible, interactionTick, isPlaying, locked, showSubtitleSettings) {
        if (controlsVisible && isPlaying && !locked && !showSubtitleSettings) {
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
    val controlsBlockPx = with(LocalDensity.current) {
        (156 + controlBottomDp).dp.toPx()
    }
    val subtitle = hostOf(stream.pageUrl)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // PlayerView 用 RESIZE_MODE_FIT，画面按比例居中，四周留黑边。
        // 字幕要贴在画面底边而不是屏幕底边，所以先算出画面高度和下边黑边。
        val displayHeight = if (maxWidth / maxHeight > videoAspect) {
            maxHeight
        } else {
            maxWidth / videoAspect
        }
        val bottomLetterbox = (maxHeight - displayHeight) / 2
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
                    text = message,
                    color = PlayerColors.Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                // 失败的具体地址：出问题时第一眼就能判断是站点变了还是网络断了
                errorReport?.let { report ->
                    Text(
                        text = report.url,
                        color = PlayerColors.Faint,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        errorMessage = null
                        errorReport = null
                        isBuffering = true
                        player.prepare()
                        player.play()
                        controlsVisible = true
                    }) {
                        Text("重试", color = PlayerColors.Accent)
                    }
                    // 复制诊断：把底层错误原因带出去，方便排查
                    errorReport?.let { report ->
                        TextButton(onClick = {
                            copyToClipboard(context, "播放诊断", report.detail)
                            Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("复制诊断", color = PlayerColors.Muted)
                        }
                    }
                    // 兜底：App 播不了时，至少还能用浏览器看
                    TextButton(onClick = { openInBrowser(context, stream.pageUrl) }) {
                        Text("浏览器打开", color = PlayerColors.Muted)
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

        caption?.let { track ->
            // 横屏画面高，字号跟着放大一点，否则在手机上看着偏小
            val displayStyle = if (isLandscape) {
                subtitleStyle.copy(
                    fontSize = (subtitleStyle.fontSize * 1.2f).coerceAtMost(SubtitleStyle.FONT_MAX),
                )
            } else {
                subtitleStyle
            }
            SubtitleOverlay(
                track = track,
                positionMs = previewMs ?: positionMs,
                style = displayStyle,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    // 固定贴在视频画面底边，控件弹出与否都不移动。
                    // 竖屏画面窄，字幕几乎压着画面下沿；横屏留一点离底距离。
                    .padding(
                        bottom = bottomLetterbox +
                            ((if (isLandscape) 24 else -10) + subtitleStyle.extraBottomDp()).dp,
                    ),
            )
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
                subtitleActive = caption != null,
                onPickSubtitle = { subtitlePicker.launch(arrayOf("*/*")) },
                onClearSubtitle = {
                    caption = null
                    subtitleStore.clear(contentKey)
                    deleteSharedSubtitle()
                },
                onOpenSubtitleSettings = { showSubtitleSettings = true },
                controlBottom = controlBottomDp,
                // 竖屏状态栏隐藏着，顶部栏要自己往下让一让，不然贴着屏幕边
                topPadding = if (isLandscape) 14.dp else 42.dp,
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

        if (showSubtitleSettings) {
            SubtitleSettingsPage(
                initial = subtitleStyle,
                subtitle = "仅对当前作品生效，下次打开自动沿用",
                onSave = { updated ->
                    subtitleStyle = updated
                    subtitleStyleStore.save(contentKey, updated)
                    uploadSubtitleStyle()
                },
                onBack = {
                    showSubtitleSettings = false
                    touch()
                },
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildPlayer(
    context: Context,
    stream: ResolvedStream,
    sharpen: SharpenLevel,
): ExoPlayer {
    val headers = buildMap {
        // UA 必须和解析页面时一致：CDN 常做「页面 UA == 媒体 UA」的一致性校验
        put("User-Agent", stream.userAgent.ifBlank { Resolver.DESKTOP_UA })
        if (stream.referer.isNotBlank()) {
            put("Referer", stream.referer)
            // Origin 与 Referer 同源。CDN 的防盗链规则常一起看这两个头，
            // 只给 Referer 缺 Origin 仍可能被判成盗链。
            runCatching {
                val uri = java.net.URI(stream.referer)
                if (uri.scheme != null && uri.host != null) {
                    put("Origin", "${uri.scheme}://${uri.host}")
                }
            }
        }
        // 贴近浏览器的常规请求头。CDN 的风控会看 Accept 家族是否齐全，
        // OkHttp 默认一个都不带，容易被判定为异常客户端。
        put("Accept", "*/*")
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
    }
    // 用共享客户端：解析页面时站点下发的 Cookie 能带到 m3u8 和分片请求上，
    // 否则浏览器能播、App 里 403。
    val upstream = OkHttpDataSource.Factory(HttpClients.client)
        .setDefaultRequestProperties(headers)
    // 播放列表不进缓存，避免缓存里的过期分片地址导致播放失败
    val hlsFactory = MediaCache.hlsDataSourceFactory(context, upstream)

    val mediaSource = HlsMediaSource.Factory(hlsFactory)
        .setAllowChunklessPreparation(true)
        .createMediaSource(MediaItem.fromUri(stream.masterUrl))

    return ExoPlayer.Builder(context)
        .build()
        .apply {
            // 特效链必须在 prepare() 之前建立，之后改只能换效果、不能新建管线
            if (sharpen.enabled) {
                setVideoEffects(listOf(SharpenEffect(sharpen.amount)))
            }
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
}

/**
 * 按片源帧率切换显示模式。找不到能整除的刷新率就保持原样，
 * 不动比乱动强：切到不对的模式反而会增加抖动。
 *
 * 帧率来自轨道 Format，部分 HLS 源不写 FRAME-RATE 属性，此时拿不到就不切。
 */
@Suppress("DEPRECATION")
private fun applyFrameRateMatch(activity: Activity, videoFps: Float): Boolean = runCatching {
    val window = activity.window ?: return@runCatching false
    val display = window.windowManager?.defaultDisplay ?: return@runCatching false
    val currentModeId = display.mode?.modeId ?: return@runCatching false
    val modes = display.supportedModes?.map {
        RefreshMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
    }.orEmpty()
    val target = RefreshRatePicker.pick(modes, currentModeId, videoFps) ?: return@runCatching false
    window.attributes = window.attributes.apply { preferredDisplayModeId = target }
    Log.i("FavourPlayer", "frame rate match: fps=$videoFps mode=$target")
    true
}.getOrDefault(false)

@Suppress("DEPRECATION")
private fun restoreDefaultDisplayMode(activity: Activity) {
    runCatching {
        val window = activity.window ?: return@runCatching
        if (window.attributes.preferredDisplayModeId == 0) return@runCatching
        window.attributes = window.attributes.apply { preferredDisplayModeId = 0 }
    }
}

private fun frameAt(positionMs: Long, durationMs: Long, config: SpriteConfig): Int {
    if (durationMs <= 0L) return 0
    val ratio = positionMs.toDouble() / durationMs.toDouble()
    return (ratio * config.frameCount).toInt().coerceIn(0, config.frameCount - 1)
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
