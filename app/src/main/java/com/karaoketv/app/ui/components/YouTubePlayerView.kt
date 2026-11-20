package com.karaoketv.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val POLYFILL_JS = """
    (function() {
        if (typeof window.queueMicrotask !== 'function') {
            window.queueMicrotask = function(cb) {
                if (typeof Promise === 'function') {
                    Promise.resolve().then(cb).catch(function(e) {
                        setTimeout(function() { throw e; }, 0);
                    });
                } else {
                    setTimeout(cb, 0);
                }
            };
        }
    })();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    modifier: Modifier = Modifier,
    onVideoEnded: () -> Unit
) {
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(POLYFILL_JS, null)
                }

                override fun onLoadResource(view: WebView?, url: String?) {
                    super.onLoadResource(view, url)
                    if (url != null && url.contains("youtube.com")) {
                        view?.evaluateJavascript(POLYFILL_JS, null)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return false
                }
            }
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    DisposableEffect(webView) {
        val jsBridge = object {
            @JavascriptInterface
            fun onEnded() {
                webView.post { onVideoEnded() }
            }
        }
        webView.addJavascriptInterface(jsBridge, "AndroidBridge")
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        update = { view ->
            val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&controls=1&rel=0&modestbranding=1&playsinline=1&enablejsapi=1"
            if (view.url != embedUrl) {
                view.loadUrl(embedUrl, hashMapOf("Referer" to "https://www.youtube.com"))
            }
        }
    )
}
