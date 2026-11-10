package com.biliflow.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.QrLoginInfo
import com.biliflow.app.data.QrPollResult
import com.biliflow.app.data.UiMessages
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 登录：手机号验证码为主，扫码为辅。 */
@Composable
fun LoginSheet(onDismiss: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0=手机号 1=扫码
    var tip by remember { mutableStateOf("") }
    var showWeb by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var qr by remember { mutableStateOf<QrLoginInfo?>(null) }
    var status by remember { mutableStateOf("正在生成二维码") }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken, mode) {
        if (mode != 1) return@LaunchedEffect
        qr = null
        error = null
        finished = false
        status = "正在生成二维码"
        try {
            qr = AuthManager.generateQr()
            status = "请使用哔哩哔哩 App 扫码"
        } catch (exception: Exception) {
            error = exception.message ?: "二维码生成失败"
            status = "二维码生成失败"
        }
    }

    LaunchedEffect(qr?.qrKey) {
        val key = qr?.qrKey ?: return@LaunchedEffect
        while (!finished) {
            delay(2000)
            when (val result = AuthManager.pollQr(key)) {
                QrPollResult.Waiting -> status = "请使用哔哩哔哩 App 扫码"
                QrPollResult.Scanned -> status = "已扫码，请在手机上确认"
                QrPollResult.Expired -> {
                    status = "二维码已过期"
                    break
                }

                is QrPollResult.Success -> {
                    finished = true
                    status = "登录成功"
                    UiMessages.show("登录成功：${result.user.name.ifBlank { "B 站账号" }}")
                    delay(300)
                    onDismiss()
                    break
                }

                is QrPollResult.Failure -> {
                    error = result.message
                    status = "登录失败"
                    break
                }
            }
        }
    }

    val bitmap by produceState<Bitmap?>(initialValue = null, qr?.url) {
        val content = qr?.url ?: return@produceState
        value = withContext(Dispatchers.IO) { createQrBitmap(content, 720) }
    }

    val biliCookies = {
        val manager = android.webkit.CookieManager.getInstance()
        listOf(
            "https://www.bilibili.com",
            "https://passport.bilibili.com",
            "https://api.bilibili.com"
        ).joinToString("; ") { manager.getCookie(it).orEmpty() }
    }

    LaunchedEffect(showWeb) {
        if (!showWeb) return@LaunchedEffect
        while (true) {
            delay(1500)
            val cookie = biliCookies()
            if (cookie.contains("SESSDATA=")) {
                val user = runCatching { AuthManager.loginFromWebCookie(cookie) }.getOrNull()
                if (user != null) {
                    UiMessages.show("登录成功：${user.name.ifBlank { "B 站账号" }}")
                    showWeb = false
                    onDismiss()
                    break
                }
            }
        }
    }

    if (showWeb) {
        Dialog(
            onDismissRequest = { showWeb = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false
            )
        ) {
            InAppWebScreen(
                url = "https://passport.bilibili.com/login",
                onClose = {
                    showWeb = false
                    scope.launch {
                        val user = runCatching { AuthManager.loginFromWebCookie(biliCookies()) }.getOrNull()
                        if (user != null) {
                            UiMessages.show("登录成功：${user.name.ifBlank { "B 站账号" }}")
                            onDismiss()
                        } else {
                            tip = "未检测到登录态，请完成登录后再关闭"
                        }
                    }
                }
            )
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            enter.animateTo(1f, BiliMotion.TransitionEnter)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f * enter.value))
                .navigationBarsPadding()
                .imePadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = enter.value
                        val scale = 0.96f + 0.04f * enter.value
                        scaleX = scale
                        scaleY = scale
                        translationY = (1f - enter.value) * 32f
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(appSurface())
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "登录哔哩哔哩",
                            color = appText(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "同步点赞、收藏、关注、投币、评论与弹幕",
                            color = appTextMuted(),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    AppIconButton(Icons.Filled.Close, "关闭登录", onDismiss)
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(appField())
                        .padding(4.dp)
                ) {
                    LoginTab("手机号登录", mode == 0, { mode = 0 }, Modifier.weight(1f))
                    LoginTab("扫码登录", mode == 1, { mode = 1 }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))

                Crossfade(
                    targetState = mode,
                    animationSpec = tween(200),
                    label = "loginMode",
                    modifier = Modifier.fillMaxWidth()
                ) { currentMode ->
                    if (currentMode == 0) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "手机号 + 短信验证码",
                                color = appText(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "在哔哩哔哩官方安全页完成验证，登录成功后自动返回。",
                                color = appTextMuted(),
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(appSurfaceRaised())
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(appAccentSoft()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = BiliAccent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "手机号与验证码仅交由官方页面校验，应用不会获取你的密码。",
                                    color = appTextMuted(),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(18.dp))
                            AppPrimaryButton(
                                text = "打开官方安全登录页",
                                onClick = { tip = ""; showWeb = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (tip.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(tip, color = appTextMuted(), fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    } else {
                        val refreshing = status == "正在生成二维码"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(appSurfaceRaised())
                                .border(1.dp, appDivider(), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(224.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                val image = bitmap
                                if (image != null) {
                                    Image(
                                        bitmap = image.asImageBitmap(),
                                        contentDescription = "登录二维码",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                            .graphicsLayer {
                                                alpha = if (status == "二维码已过期") 0.28f else 1f
                                            }
                                    )
                                } else if (refreshing) {
                                    CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp)
                                } else {
                                    Text("二维码暂不可用", color = Color(0xFF747984), fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = status,
                                color = if (error == null) appText() else MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (error != null) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    error.orEmpty(),
                                    color = appTextMuted(),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 3
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            AppSecondaryButton(
                                text = if (refreshing) "正在生成…" else "刷新二维码",
                                onClick = { refreshToken++ },
                                enabled = !refreshing,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = appTextMuted(),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "登录 Cookie 仅保存在本机",
                        color = appTextMuted(),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) appSurfaceRaised() else Color.Transparent,
        animationSpec = tween(180),
        label = "loginTabBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) appText() else appTextMuted(),
        animationSpec = tween(180),
        label = "loginTabText"
    )
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .pressClickable(pressedScale = 0.97f) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun createQrBitmap(content: String, size: Int): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}
