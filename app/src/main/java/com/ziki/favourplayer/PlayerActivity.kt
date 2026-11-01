package com.ziki.favourplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
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

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val pageUrl = urlFromIntent(intent).orEmpty()

        setContent {
            FavourPlayerTheme {
                var state by remember { mutableStateOf<UiState>(UiState.Loading) }

                LaunchedEffect(pageUrl) {
                    if (pageUrl.isBlank()) {
                        state = UiState.Failed("没有收到播放地址")
                        return@LaunchedEffect
                    }
                    Log.i("FavourPlayer", "resolve start: $pageUrl")
                    state = try {
                        val resolved = withContext(Dispatchers.IO) { Resolver.resolve(pageUrl) }
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
                    is UiState.Ready -> PlayerScreen(current.stream) { finish() }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

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
