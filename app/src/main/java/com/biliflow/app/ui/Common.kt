package com.biliflow.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.ImageLoader
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = collectAsState()

@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = appText()
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressClickable(pressedScale = 0.94f) { onClick() },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(23.dp))
    }
}

@Composable
fun AppPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        if (onBack != null) {
            AppIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 12.dp else 4.dp)
        ) {
            Text(
                text = title,
                color = appText(),
                fontSize = if (onBack == null) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Text(text = subtitle, color = appTextMuted(), fontSize = 11.sp, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BiliAccent, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, appDivider()),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = appText()),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AppEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String = "",
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(appAccentSoft(), CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BiliAccent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = appText(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (description.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = description,
                color = appTextMuted(),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
        if (!actionLabel.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = BiliAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AppErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    AppEmptyState(
        title = "内容暂时没有加载出来",
        description = message,
        icon = Icons.Outlined.CloudOff,
        actionLabel = "重新加载",
        onAction = onRetry,
        modifier = modifier
    )
}

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidth: Int = 720,
    placeholder: Color = Color.Unspecified
) {
    val placeholderColor = if (placeholder == Color.Unspecified) appField() else placeholder
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, targetWidth) {
        if (url.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        // 加载期间保留旧图，避免换 URL 时闪 placeholder
        val loaded = ImageLoader.load(url, targetWidth)
        if (loaded != null) bitmap = loaded
    }
    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(220),
        label = "imageFade"
    )
    Box(modifier = modifier.background(placeholderColor)) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { this.alpha = alpha },
                contentScale = contentScale
            )
        }
    }
}

@Composable
fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
}

fun formatCount(count: Long): String = when {
    count >= 100_000_000L -> compact(count / 100_000_000.0) + "亿"
    count >= 10_000L -> compact(count / 10_000.0) + "万"
    else -> count.toString()
}

private fun compact(value: Double): String {
    val text = if (value >= 100) {
        value.roundToInt().toString()
    } else {
        String.format(Locale.CHINA, "%.1f", value)
    }
    return text.trimEnd('0').trimEnd('.')
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "00:00"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remain = seconds % 60
    return if (hours > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, remain)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", minutes, remain)
    }
}

fun formatTimeAgo(timeSeconds: Long): String {    if (timeSeconds <= 0L) return ""
    val diff = (System.currentTimeMillis() / 1000L - timeSeconds).coerceAtLeast(0L)
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86_400 -> "${diff / 3600}小时前"
        diff < 30L * 86_400 -> "${diff / 86_400}天前"
        else -> SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(timeSeconds * 1000L))
    }
}

fun prettyMessage(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("{")) return raw
    val json = runCatching { org.json.JSONObject(trimmed) }.getOrNull() ?: return raw
    val title = json.optString("title")
    val text = json.optString("text")
    return when {
        title.isNotBlank() && text.isNotBlank() && title != text -> "$title · $text"
        title.isNotBlank() -> title
        text.isNotBlank() -> text
        else -> raw
    }
}

fun qualityLabel(qn: Int): String = when (qn) {
    80 -> "1080P"
    64 -> "720P"
    32 -> "480P"
    16 -> "360P"
    else -> "720P"
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        UiMessages.show("没有可打开此链接的应用")
    }
}

fun openBiliVideo(context: Context, item: VideoItem) {
    openUrl(context, item.webUrl)
}

fun openBiliSpace(context: Context, mid: Long) {
    if (mid > 0L) openUrl(context, "https://space.bilibili.com/$mid")
}

fun shareVideo(context: Context, item: VideoItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.webUrl}")
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "分享视频"))
    }
}
