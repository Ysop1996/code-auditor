package de.lifeos.android.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.*

class AnonymousBrowserSandbox(private val context: Context) {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun initializeIsolatedSession(useTorProxy: Boolean = false) {
        mainHandler.post {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    databaseEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    setGeolocationEnabled(false)
                    allowFileAccess = false
                    allowContentAccess = false
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
                }

                CookieManager.getInstance().setAcceptCookie(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                if (useTorProxy) {
                    System.setProperty("socksProxyHost", "127.0.0.1")
                    System.setProperty("socksProxyPort", "9050")
                }

                clearCache(true)
                clearHistory()
                clearFormData()
            }
        }
    }

    fun executeAnonymousSearch(
        query: String,
        onPageLoaded: (title: String, url: String) -> Unit,
        onFrameRendered: (frameSignal: DoubleArray) -> Unit
    ) {
        mainHandler.post {
            webView?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageLoaded(view?.title ?: "Unbekannt", url ?: "")
                    captureFrameSignal(onFrameRendered)
                }
            }
            val sanitizedQuery = query.replace(" ", "+")
            webView?.loadUrl("https://html.duckduckgo.com/html/?q=$sanitizedQuery")
        }
    }

    fun captureFrameSignal(onSignalExtracted: (DoubleArray) -> Unit) {
        mainHandler.post {
            val wv = webView ?: return@post
            val width = wv.width.coerceAtLeast(320)
            val height = wv.height.coerceAtLeast(480)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            wv.draw(canvas)

            val scaled = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
            val signal = DoubleArray(256)
            var idx = 0
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    val pixel = scaled.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    signal[idx++] = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0
                }
            }
            bitmap.recycle()
            scaled.recycle()
            onSignalExtracted(signal)
        }
    }

    fun extractCleanedDomText(onResult: (String) -> Unit) {
        mainHandler.post {
            webView?.evaluateJavascript(
                """
                (function() {
                    var removeSelectors = ['script', 'style', 'nav', 'footer', 'iframe', 'header', '.ad', '#cookie-banner'];
                    removeSelectors.forEach(function(sel) {
                        var elements = document.querySelectorAll(sel);
                        elements.forEach(function(el) { el.remove(); });
                    });
                    return document.body.innerText;
                })();
                """.trimIndent()
            ) { rawResult ->
                val cleaned = rawResult
                    ?.replace("\\n", "\n")
                    ?.replace("^\"|\"$".toRegex(), "")
                    ?.lines()
                    ?.map { it.trim() }
                    ?.filter { it.length > 30 }
                    ?.take(20)
                    ?.joinToString("\n") ?: ""
                onResult(cleaned)
            }
        }
    }

    fun destroyAndWipeSession() {
        mainHandler.post {
            webView?.apply {
                stopLoading()
                clearCache(true)
                clearHistory()
                clearFormData()
                destroy()
            }
            webView = null
        }
    }
}
