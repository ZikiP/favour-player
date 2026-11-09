package com.ziki.favourplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class PlayerActivity : ComponentActivity() {

    /** 是否处于画中画；Compose 要跟着它决定要不要显示自定义控件。 */
    private var inPip by mutableStateOf(false)

    /** 播放器状态快照，用来算画中画比例和系统按钮图标。 */
    private var playback by mutableStateOf(PipPlayback())

    /** 当前播放的作品地址；从扩展/媒体面板再次打开时原地换片，不重开窗口。 */
    private var pageUrl by mutableStateOf("")
    private var fromStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        PipBridge.closeAction = { finish() }

        pageUrl = urlFromIntent(intent).orEmpty()
        fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)

        setContent {
            FavourPlayerTheme {
                var state by remember { mutableStateOf<UiState>(UiState.Loading) }

                LaunchedEffect(pageUrl) {
                    state = UiState.Loading
                    if (pageUrl.isBlank()) {
                        state = UiState.Failed("没有收到播放地址")
                        return@LaunchedEffect
                    }
                    Log.i("FavourPlayer", "resolve start: $pageUrl")
                    state = try {
                        val resolved = withContext(Dispatchers.IO) {
                            Resolver.resolve(
                                pageUrl,
                                RuleStore(applicationContext).loadRules(),
                            )
                        }
                        Log.i(
                            "FavourPlayer",
                            "resolved uuid=${resolved.uuid} sprite=${resolved.sprite != null}",
                        )
                        UiState.Ready(resolved)
                    } catch (error: Throwable) {
                        Log.e("FavourPlayer", "resolve failed", error)
                        UiState.Failed(error.message ?: "解析失败")
                    }
                }

                when (val current = state) {
                    UiState.Loading -> PlayerLoading()
                    is UiState.Failed -> PlayerFailure(current.message) { finish() }
                    is UiState.Ready -> PlayerScreen(
                        stream = current.stream,
                        contentKey = pageUrl,
                        fromStart = fromStart,
                        onBack = { finish() },
                        isInPip = inPip,
                        onEnterPip = { enterPip() },
                        onPlaybackChanged = { updatePlayback(it) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = urlFromIntent(intent).orEmpty()
        if (next == pageUrl) return
        fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)
        pageUrl = next
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) hideSystemBars()
    }

    /** 播放中退到后台就自动进画中画，别把声音丢在后台。 */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!inPip && playback.isPlaying) enterPip()
    }

    override fun onDestroy() {
        PipBridge.player = null
        PipBridge.closeAction = null
        super.onDestroy()
    }

    private fun updatePlayback(value: PipPlayback) {
        playback = value
        PipBridge.player = value.player
        if (inPip) {
            runCatching { setPictureInPictureParams(buildPipParams()) }
                .onFailure { Log.w(TAG, "update pip params failed", it) }
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (inPip) return
        runCatching {
            val params = buildPipParams()
            setPictureInPictureParams(params)
            enterPictureInPictureMode(params)
        }.onFailure { Log.w(TAG, "enter picture-in-picture failed", it) }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val width = playback.width
        val height = playback.height
        if (width > 0 && height > 0) {
            // 系统限定比例范围，太极端的竖屏或超宽画面要夹一下
            val ratio = (width.toFloat() / height.toFloat()).coerceIn(1f / 2.39f, 2.39f)
            builder.setAspectRatio(Rational((ratio * 1000f).roundToInt(), 1000))
        }
        builder.setActions(remoteActions())
        return builder.build()
    }

    /** 画中画窗口里的系统按钮：播放/暂停，外加关闭。 */
    private fun remoteActions(): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val playing = playback.isPlaying
        val toggle = RemoteAction(
            Icon.createWithResource(
                this,
                if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
            ),
            if (playing) "暂停" else "播放",
            if (playing) "暂停播放" else "继续播放",
            pipPendingIntent(
                if (playing) PipBridge.ACTION_PAUSE else PipBridge.ACTION_PLAY,
                1,
            ),
        )
        val close = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_pip_close),
            "关闭",
            "关闭画中画",
            pipPendingIntent(PipBridge.ACTION_CLOSE, 2),
        )
        return listOf(toggle, close)
    }

    private fun pipPendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            this,
            requestCode,
            Intent(this, PipReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun urlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            if (data.scheme.equals("favourplayer", ignoreCase = true)) {
                val target = data.getQueryParameter("url") ?: return null
                return target.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            }
            return data.toString()
        }
        return intent.getStringExtra(EXTRA_PAGE_URL)
    }

    companion object {
        const val EXTRA_PAGE_URL = "page_url"
        const val EXTRA_FROM_START = "from_start"
        private const val TAG = "FavourPlayer"
    }
}

private sealed interface UiState {
    data object Loading : UiState
    data class Ready(val stream: ResolvedStream) : UiState
    data class Failed(val message: String) : UiState
}

@Composable
private fun PlayerLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Bg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = PlayerColors.Accent)
    }
}

@Composable
private fun PlayerFailure(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Bg)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = PlayerColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text("返回", color = PlayerColors.Accent)
            }
        }
    }
}
