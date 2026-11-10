package com.biliflow.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.VideoUploader
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

/** 原生投稿页：本机选片 + 参数填写 + 本地草稿（不跳浏览器）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var tid by remember { mutableStateOf("21") }
    var tag by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var onlySelf by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            videoUri = uri
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "已选择视频"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = appSurface(),
        contentColor = appText(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = appTextMuted().copy(alpha = 0.45f)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("投稿视频", color = appText(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "本机选择并直接上传到 B 站，提交后可在创作中心查看审核状态",
                color = appTextMuted(),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(20.dp))

            PublishSectionLabel("01  选择视频")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(appSurfaceRaised())
                    .border(1.dp, appDivider(), RoundedCornerShape(16.dp))
                    .pressClickable(pressedScale = 0.97f) { picker.launch("video/*") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(appAccentSoft()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (videoUri == null) Icons.Filled.VideoCall else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = BiliAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (videoUri == null) "选择本地视频" else "视频已就绪",
                        color = appText(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = videoName.ifBlank { "支持 mp4、mov 等常见格式" },
                        color = appTextMuted(),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2
                    )
                    if (videoUri != null) {
                        Spacer(Modifier.height(3.dp))
                        Text("轻触可重新选择", color = BiliAccent, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            PublishSectionLabel("02  视频信息")
            Spacer(Modifier.height(6.dp))
            PublishField("标题", title, "填写一个清晰的标题") { title = it.take(80) }
            PublishField(
                label = "简介",
                value = desc,
                placeholder = "补充视频内容、来源或其他说明",
                singleLine = false,
                onChange = { desc = it.take(300) }
            )
            PublishField("分区 ID", tid, "如 17=游戏，21=生活日常") {
                tid = it.filter { c -> c.isDigit() }.take(4)
            }
            PublishField("标签", tag, "多个标签用逗号分隔") { tag = it.take(60) }

            Spacer(Modifier.height(14.dp))
            PublishSectionLabel("03  发布设置")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(appSurfaceRaised())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (onlySelf) "仅自己可见" else "公开投稿",
                        color = appText(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (onlySelf) "投稿后只有当前账号可以查看" else "审核通过后公开展示",
                        color = appTextMuted(),
                        fontSize = 11.sp
                    )
                }
                androidx.compose.material3.Switch(
                    checked = onlySelf,
                    onCheckedChange = { onlySelf = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = BiliAccent,
                        uncheckedTrackColor = appField()
                    )
                )
            }

            Spacer(Modifier.height(20.dp))
            PublishSectionLabel("04  确认并上传")
            Spacer(Modifier.height(12.dp))
            val canSubmit = !uploading && videoUri != null && title.isNotBlank()
            val submitBackground by androidx.compose.animation.animateColorAsState(
                targetValue = if (canSubmit || uploading) BiliAccent else appChip(),
                animationSpec = androidx.compose.animation.core.tween(200),
                label = "submitBackground"
            )
            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = progress,
                animationSpec = androidx.compose.animation.core.tween(300),
                label = "uploadProgress"
            )
            if (uploading) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "正在上传视频，请保持应用开启",
                        color = appTextMuted(),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        color = appText(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(appField())
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(BiliAccent)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(submitBackground)
                    .pressClickable(enabled = canSubmit, pressedScale = 0.97f) {
                        val uri = videoUri ?: return@pressClickable
                        val tidValue = tid.toIntOrNull() ?: 21
                        scope.launch {
                            uploading = true
                            progress = 0f
                            try {
                                val result = VideoUploader.upload(
                                    context = context,
                                    uri = uri,
                                    title = title.trim(),
                                    desc = desc.trim(),
                                    tid = tidValue,
                                    tag = tag.trim(),
                                    isOnlySelf = onlySelf,
                                    onProgress = { progress = it }
                                )
                                UiMessages.show("投稿成功：${result.bvid}")
                                onDismiss()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                UiMessages.show(error.message ?: "上传失败")
                            } finally {
                                uploading = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uploading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uploading) "上传中 ${(progress * 100).toInt()}%" else "上传投稿",
                        color = if (canSubmit || uploading) Color.White else appTextMuted(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PublishSectionLabel(text: String) {
    Text(text, color = appTextMuted(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PublishField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = appTextMuted(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 5,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 48.dp else 96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(appField())
                .padding(horizontal = 14.dp, vertical = 13.dp),
            textStyle = TextStyle(color = appText(), fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BiliAccent),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = appTextMuted(), fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    inner()
                }
            }
        )
    }
}
