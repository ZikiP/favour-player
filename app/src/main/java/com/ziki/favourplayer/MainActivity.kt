package com.ziki.favourplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var libraryModel: LibraryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        libraryModel = ViewModelProvider(this)[LibraryViewModel::class.java]

        sharedUrl(intent)?.let { openPlayer(it, false) }
        setContent {
            FavourPlayerTheme {
                LibraryScreen(
                    viewModel = libraryModel,
                    onPlay = { url, fromStart -> openPlayer(url, fromStart) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::libraryModel.isInitialized) libraryModel.onForeground()
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
