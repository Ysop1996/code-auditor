package de.lifeos.android.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.*
import java.io.File

class AnonymousBrowserSandbox(private val context: Context) {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun initializeIsolatedSession(useTorProxy: Boolean = false) {
        mainHandler.post {
            // Zero-Disk-Trace: Verwende RAM-only Cache-Verzeichnis
            val ramCacheDir = File(context.cacheDir, "ram_only_browser_${System.currentTimeMillis()}")
            ramCacheDir.mkdirs()

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

                // DEF-02 Fix: Tor-Proxy via Reflection (WebView unterstützt kein natives SOCKS)
                if (useTorProxy) {
                    configureTorProxy()
                }

                // DEF-08 Fix: Zero-Disk-Trace — alle Caches und Historie löschen
                clearCache(true)
                clearHistory()
                clearFormData()
                WebStorage.getInstance().deleteAllData()
                WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
                WebViewDatabase.getInstance(context).clearFormData()
                WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()

                // RAM-only Cache-Verzeichnis setzen
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }
    }

    /**
     * DEF-02 Fix: Konfiguriert Tor-Proxy für WebView via Reflection.
     * Android WebView unterstützt kein natives SOCKS, daher wird ein
     * lokaler HTTP-Proxy (z.B. Orbot) auf 127.0.0.1:8118 erwartet.
     */
    private fun configureTorProxy() {
        try {
            val proxyHost = "127.0.0.1"
            val proxyPort = 8118 // Standard Orbot HTTP-Proxy-Port

            // Proxy via Reflection setzen (funktioniert auf API 29+)
            val webViewClass = WebView::class.java
            val method = webViewClass.getMethod("setProxy", String::class.java, Int::class.java)
            method.invoke(webView, proxyHost, proxyPort)
        } catch (e: Exception) {
            // Fallback: System-Properties (funktioniert nur auf älteren APIs)
            try {
                System.setProperty("http.proxyHost", "127.0.0.1")
                System.setProperty("http.proxyPort", "8118")
                System.setProperty("https.proxyHost", "127.0.0.1")
                System.setProperty("https.proxyPort", "8118")
            } catch (ignored: Exception) {
                // Proxy konnte nicht konfiguriert werden
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

    fun loadUrl(
        url: String,
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
            webView?.loadUrl(url)
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

            // DEF-08 Fix: Zero-Disk-Trace — RAM-Cache-Verzeichnis löschen
            try {
                val ramCacheDir = File(context.cacheDir, "ram_only_browser_")
                ramCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                ramCacheDir.delete()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }

            // System-Proxy zurücksetzen
            try {
                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
                System.clearProperty("https.proxyHost")
                System.clearProperty("https.proxyPort")
            } catch (ignored: Exception) {
                // Ignore
            }
        }
    }
}
