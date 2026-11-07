package dev.breadcrumb.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat

class MainActivity : ComponentActivity() {
    private val notebook: NotebookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.BLACK,
                ),
        )
        setContent { BreadcrumbTheme { BreadcrumbApp(notebook) } }
        if (savedInstanceState == null) receive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        if (intent.type == "text/plain") {
            notebook.receiveLink(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        } else if (intent.type?.startsWith("image/") == true) {
            IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    android.net.Uri::class.java,
                )
                ?.let { notebook.importPhoto("scan", it) }
        }
    }
}
