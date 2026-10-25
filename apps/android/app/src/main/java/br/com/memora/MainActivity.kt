package br.com.memora

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebMessageCompat
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var resumed = false
    private var contentReady = false
    private var awakeRequested = false

    @SuppressLint("SetJavaScriptEnabled") // Packaged allowlisted content with an origin-scoped message channel.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView = WebView(this).apply {
            settings.apply {
                // React runs only from the packaged local origin.
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    contentReady = LocalContentPolicy.allows(url)
                    syncVisibility()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    !LocalContentPolicy.allows(request.url.toString())

                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse {
                    if (request.method == "GET" && LocalContentPolicy.allows(request.url.toString())) {
                        loader.shouldInterceptRequest(request.url)?.let { return it }
                    }
                    // Never fall through to DNS/network, even for missing local assets.
                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                        emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "MemoraHost",
                setOf("https://appassets.androidplatform.net")) { _, message, _, isMainFrame, _ ->
                if (isMainFrame && message.type == WebMessageCompat.TYPE_STRING) {
                    when (message.data) {
                        "awake:1" -> awakeRequested = true
                        "awake:0" -> awakeRequested = false
                    }
                    syncScreenFlag()
                }
            }
        } else {
            setContentView(TextView(this).apply { setText(R.string.webview_unavailable) })
            return
        }
        setContentView(webView)
        window.setDecorFitsSystemWindows(false)
        webView.loadUrl(LocalContentPolicy.ENTRY)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        webView.onResume()
        syncVisibility()
    }

    override fun onPause() {
        resumed = false
        syncVisibility()
        webView.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        if (::webView.isInitialized) syncVisibility()
    }

    private fun syncScreenFlag() {
        if (resumed && hasWindowFocus() && awakeRequested) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun syncVisibility() {
        val visible = resumed && hasWindowFocus()
        syncScreenFlag()
        if (contentReady) webView.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('memora:visibility',{detail:$visible}));", null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = when (event.keyCode) {
            KeyEvent.KEYCODE_MENU -> "menu"
            KeyEvent.KEYCODE_BACK -> "back"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "toggle"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "play"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "pause"
            else -> null
        }
        if (command == null || !contentReady) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('memora:command',{detail:'$command'}));", null)
        }
        return true
    }

    override fun onDestroy() {
        contentReady = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
