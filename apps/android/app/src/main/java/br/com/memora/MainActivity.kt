package br.com.memora

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView = WebView(this).apply {
            settings.apply {
                // I1 only renders static HTML; enable JS only when I2 requires it.
                javaScriptEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
            }
            webViewClient = object : WebViewClient() {
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
            loadUrl(LocalContentPolicy.ENTRY)
        }
        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
