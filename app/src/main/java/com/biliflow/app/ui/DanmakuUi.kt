package com.biliflow.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.DanmakuItem

@Composable
fun DanmakuOverlay(
    items: List<DanmakuItem>,
    position: androidx.compose.runtime.State<Long>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled || items.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val sorted = remember(items) { items.sortedBy { it.timeMs } }
    val measured = remember(items) { HashMap<Int, androidx.compose.ui.text.TextLayoutResult>() }
    val styleCache = remember { HashMap<Int, TextStyle>() }

    // 轨道一次性稳定分配，避免每帧重排导致弹幕上下跳动
    val lanes = remember(sorted) {
        val laneCount = 12
        val scrollFree = FloatArray(laneCount) { 0f }
        val topFree = FloatArray(laneCount) { 0f }
        val bottomFree = FloatArray(laneCount) { 0f }
        IntArray(sorted.size) { -1 }.also { out ->
            sorted.forEachIndexed { i, item ->
                val t = item.timeMs.toFloat()
                out[i] = when (item.mode) {
                    4 -> {
                        val f = bottomFree.indexOfFirst { it <= t }
                        if (f >= 0) bottomFree[f] = t + 4_000f
                        f
                    }
                    5 -> {
                        val f = topFree.indexOfFirst { it <= t }
                        if (f >= 0) topFree[f] = t + 4_000f
                        f
                    }
                    else -> {
                        val f = scrollFree.indexOfFirst { it <= t }
                        if (f >= 0) scrollFree[f] = t + 8_500f
                        f
                    }
                }
            }
        }
    }

    Canvas(modifier.clipToBounds()) {
        // 读 State 只触发重绘，不触发重组
        // 提前 150ms 切入，抵消解码/合成延迟，避免弹幕“贴边才出现”
        val positionMs = position.value + 150L
        val width = size.width
        val height = size.height
        val laneHeight = 27.dp.toPx()
        val topStart = height * 0.04f
        val laneCount = ((height * 0.72f) / laneHeight).toInt().coerceIn(4, 14)
        val windowMs = 8_500L

        // 只遍历时间窗内的弹幕（items 已按时间排序）
        var lo = 0
        var hi = sorted.size - 1
        var start = sorted.size
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid].timeMs >= positionMs - windowMs) {
                start = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }

        var index = start
        while (index < sorted.size) {
            val item = sorted[index]
            val elapsed = positionMs - item.timeMs
            if (elapsed < 0L) break
            val lane = lanes[index]
            index++
            if (elapsed > windowMs) continue
            if (lane < 0) continue
            val alpha = when {
                elapsed > 8000L -> ((8500L - elapsed) / 500f).coerceIn(0f, 1f)
                else -> 1f
            } * 0.92f

            val baseColor = 0xFF000000.toInt() or item.color
            val style = styleCache.getOrPut(item.fontSize) {
                TextStyle(
                    color = Color(baseColor),
                    fontSize = (item.fontSize.coerceIn(18, 36) * 0.62f).sp,
                    fontWeight = FontWeight.Medium,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(1f, 1f),
                        blurRadius = 3f
                    )
                )
            }
            val layout = measured.getOrPut(item.text.hashCode() * 31 + item.fontSize) {
                textMeasurer.measure(
                    text = AnnotatedString(item.text),
                    style = style,
                    maxLines = 1
                )
            }
            val textWidth = layout.size.width.toFloat()
            val y = when (item.mode) {
                4 -> height * 0.72f + (lane % 4) * laneHeight
                5 -> topStart + (lane % 4) * laneHeight
                else -> topStart + (lane % laneCount) * laneHeight
            }
            val x = when (item.mode) {
                4, 5 -> (width - textWidth) / 2f
                else -> width - (elapsed / 8500f) * (width + textWidth)
            }
            if (x > -textWidth && x < width) {
                drawText(layout, topLeft = Offset(x, y), alpha = alpha)
            }
        }
    }
}

@Composable
fun DanmakuBar(
    enabled: Boolean,
    danmakuOn: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    loggedIn: Boolean,
    onRequireLogin: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    var expanded by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }

    Crossfade(
        targetState = expanded,
        animationSpec = tween(200),
        label = "danmakuBar"
    ) { isExpanded ->
        if (isExpanded) {
            Row(
                modifier = modifier
                    .imePadding()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it.take(100) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(BiliAccent),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) {
                                Text(
                                    text = "发个弹幕见证当下",
                                    color = Color.White.copy(alpha = 0.52f),
                                    fontSize = 13.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (input.isBlank()) Color.White.copy(alpha = 0.12f) else BiliAccent)
                        .pressClickable(enabled = input.isNotBlank(), pressedScale = 0.94f) {
                            val text = input.trim()
                            if (text.isNotBlank()) {
                                onSend(text)
                                input = ""
                                expanded = false
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送弹幕",
                        tint = if (input.isBlank()) Color.White.copy(alpha = 0.45f) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .pressClickable(pressedScale = 0.94f) {
                            expanded = false
                            focusManager.clearFocus()
                            keyboard?.hide()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "收起弹幕输入",
                        tint = Color.White.copy(alpha = 0.68f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f))
                        .pressClickable(pressedScale = 0.94f) { onToggleDanmaku() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "弹",
                        color = if (danmakuOn) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.48f))
                        .pressClickable(pressedScale = 0.96f) {
                            if (loggedIn) expanded = true else onRequireLogin()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "发个弹幕见证当下",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
