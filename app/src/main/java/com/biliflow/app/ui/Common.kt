package com.biliflow.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.ImageLoader
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun <T> StateFlow<T>.collectAsStateCompat(): State<T> = collectAsState()

@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    targetWidth: Int = 720,
    placeholder: Color = Color(0xFF1B1B1B)
) {
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
        animationSpec = tween(280),
        label = "imageFade"
    )
    androidx.compose.foundation.layout.Box(modifier = modifier.background(placeholder)) {
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
        Toast.makeText(context, "没有可打开此链接的应用", Toast.LENGTH_SHORT).show()
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
