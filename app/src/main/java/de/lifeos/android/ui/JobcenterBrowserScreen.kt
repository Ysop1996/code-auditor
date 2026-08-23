package de.lifeos.android.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

val BrowserBackground = Color(0xFF0D1117)
val BrowserSurface = Color(0xFF161B22)
val BrowserAccentCyan = Color(0xFF00E5FF)
val BrowserAccentGreen = Color(0xFF00E676)
val BrowserTextPrimary = Color(0xFFE6EDF3)
val BrowserTextSecondary = Color(0xFF7D8590)
val BrowserDivider = Color(0xFF21262D)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JobcenterBrowserScreen(
    onBack: () -> Unit,
    initialUrl: String = "https://www.jobcenter-online.de"
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("Jobcenter Online") }
    var webView: WebView? by remember { mutableStateOf(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrowserBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            BrowserHeader(
                onBack = onBack,
                pageTitle = pageTitle,
                isLoading = isLoading
            )

            // URL Bar
            UrlBar(
                inputUrl = inputUrl,
                onInputChange = { inputUrl = it },
                onGo = {
                    currentUrl = if (inputUrl.startsWith("http")) inputUrl else "https://$inputUrl"
                    inputUrl = currentUrl
                    webView?.loadUrl(currentUrl)
                },
                onRefresh = { webView?.reload() }
            )

            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            setGeolocationEnabled(false)
                            allowFileAccess = false
                            allowContentAccess = false
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"
                        }

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                pageTitle = view?.title ?: "Jobcenter Online"
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                            }
                        }

                        webView = this
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = BrowserAccentCyan,
                trackColor = BrowserDivider
            )
        }
    }
}

@Composable
fun BrowserHeader(onBack: () -> Unit, pageTitle: String, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrowserSurface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = BrowserAccentCyan
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pageTitle.take(40),
                color = BrowserTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isLoading) BrowserAccentCyan else BrowserAccentGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLoading) "Lädt..." else "Verbunden",
                    color = BrowserTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Sicher",
            tint = BrowserAccentGreen,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun UrlBar(
    inputUrl: String,
    onInputChange: (String) -> Unit,
    onGo: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrowserBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputUrl,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BrowserTextPrimary,
                unfocusedTextColor = BrowserTextPrimary,
                cursorColor = BrowserAccentCyan,
                focusedBorderColor = BrowserAccentCyan.copy(alpha = 0.5f),
                unfocusedBorderColor = BrowserDivider,
                focusedContainerColor = BrowserSurface,
                unfocusedContainerColor = BrowserSurface
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Aktualisieren",
                tint = BrowserAccentCyan
            )
        }
    }
}
