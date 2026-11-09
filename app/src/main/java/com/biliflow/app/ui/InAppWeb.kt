package com.biliflow.app.ui

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.biliflow.app.data.Http

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/** 应用内网页容器（编辑资料、主页等用），不再跳外部浏览器。 */
@Composable
fun InAppWebScreen(url: String, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var lastUrl by remember { mutableStateOf(url) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .screenEnter()
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF161823))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .noRippleClickable {
                        val wv = webViewRef
                        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
                    }
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title.ifBlank { "哔哩哔哩" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "关闭",
                color = Color(0xFFB0B0B3),
                fontSize = 12.sp,
                modifier = Modifier
                    .noRippleClickable { onClose() }
                    .padding(horizontal = 8.dp)
            )
        }
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250))
        ) {
            LinearProgressIndicator(
                color = Color(0xFFFE2C55),
                trackColor = Color(0xFF161823),
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        val cookies = CookieManager.getInstance()
                        cookies.setAcceptCookie(true)
                        Http.cookie.split(";").forEach { part ->
                            val trimmed = part.trim()
                            if (trimmed.isNotBlank()) {
                                cookies.setCookie(".bilibili.com", trimmed)
                                cookies.setCookie("https://space.bilibili.com", trimmed)
                            }
                        }
                        cookies.flush()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = MOBILE_UA
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                loading = false
                                canGoBack = view?.canGoBack() == true
                                title = view?.title.orEmpty()
                                lastUrl = pageUrl.orEmpty()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                if (!t.isNullOrBlank()) title = t
                            }
                        }
                        webViewRef = this
                        loadUrl(url)
                    }
                },
                update = { view ->
                    if (view.url.isNullOrBlank()) view.loadUrl(url)
                    canGoBack = view.canGoBack()
                }
            )
        }
    }
}
