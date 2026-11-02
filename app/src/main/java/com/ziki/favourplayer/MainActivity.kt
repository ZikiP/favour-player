package com.ziki.favourplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var libraryModel: LibraryViewModel

    /** 本次启动已经由启动流程负责恢复，onResume 就不要再抢一次。 */
    private var startupRestoreHandled = false

    @Volatile
    private var restoreInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        libraryModel = ViewModelProvider(this)[LibraryViewModel::class.java]

        // 重装后首次打开、且本地还没恢复过：先从服务端把偏好拿回来再进主界面，
        // 否则界面会先按默认值渲染一遍再跳变
        val needsRestore = ProfileSync.needsRestore(this)
        startupRestoreHandled = needsRestore

        sharedUrl(intent)?.let { openPlayer(it, false) }
        setContent {
            FavourPlayerTheme {
                var restoring by remember { mutableStateOf(needsRestore) }
                if (restoring) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PlayerColors.Bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = PlayerColors.Accent)
                    }
                    LaunchedEffect(Unit) {
                        val restored = withContext(Dispatchers.IO) {
                            ProfileSync.restore(applicationContext)
                        }
                        if (!restored) {
                            // 没恢复成功（多半是断网），交回给 onResume 继续重试
                            startupRestoreHandled = false
                        }
                        restoring = false
                    }
                } else {
                    LibraryScreen(
                        viewModel = libraryModel,
                        onPlay = { url, fromStart -> openPlayer(url, fromStart) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::libraryModel.isInitialized) libraryModel.onForeground()
        // 重装后第一次启动时往往还没填服务地址，恢复会被跳过。
        // 用户补填完地址回到这里时再试一次，避免「必须重启 App 才恢复」。
        restoreInBackground()
    }

    private fun restoreInBackground() {
        if (startupRestoreHandled || restoreInFlight) return
        if (!ProfileSync.needsRestore(this)) return
        restoreInFlight = true
        val appContext = applicationContext
        Thread {
            try {
                if (ProfileSync.restore(appContext)) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) recreate()
                    }
                }
            } finally {
                restoreInFlight = false
            }
        }.start()
    }

    /**
     * 离开 App 时把偏好推到中间服务。内容没变会跳过，所以频繁切换前后台
     * 也只是几次几十 KB 的比较，不会重复上传。
     */
    override fun onStop() {
        super.onStop()
        val appContext = applicationContext
        Thread {
            runCatching { ProfileSync.upload(appContext) }
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUrl(intent)?.let { openPlayer(it, false) }
    }

    private fun openPlayer(rawUrl: String, fromStart: Boolean) {
        val url = rawUrl.trim()
        if (url.isEmpty()) return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, url)
                .putExtra(PlayerActivity.EXTRA_FROM_START, fromStart),
        )
    }

    private fun sharedUrl(intent: Intent?): String? {
        if (intent == null) return null
        val payload = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        return Regex("""https?://\S+""").find(payload)?.value
    }
}
