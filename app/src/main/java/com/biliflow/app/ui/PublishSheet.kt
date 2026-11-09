package com.biliflow.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
        dragHandle = { BottomSheetDefaults.DragHandle(color = appTextMuted()) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("投稿视频", color = appText(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "应用内直接上传到 B 站（upos 分片），提交后可在创作中心查看审核状态",
                color = appTextMuted(),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (LocalAppDark.current) Color(0xFF262626) else Color(0xFFF1F1F2))
                    .noRippleClickable { picker.launch("video/*") }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.VideoCall, null, tint = BiliAccent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (videoUri == null) "选择视频" else "已选择",
                        color = appText(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = videoName.ifBlank { "支持 mp4/mov 等常见格式" },
                        color = appTextMuted(),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            PublishField("标题", title) { title = it.take(80) }
            PublishField("简介", desc) { desc = it.take(300) }
            PublishField("分区 ID（如 17=游戏，21=生活日常）", tid) { tid = it.filter { c -> c.isDigit() }.take(4) }
            PublishField("标签（逗号分隔）", tag) { tag = it.take(60) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("仅自己可见", color = appText(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    checked = onlySelf,
                    onCheckedChange = { onlySelf = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = BiliAccent)
                )
            }

            Spacer(Modifier.height(16.dp))
            val canSubmit = !uploading && videoUri != null && title.isNotBlank()
            val submitBackground by androidx.compose.animation.animateColorAsState(
                targetValue = if (canSubmit || uploading) BiliAccent else Color(0xFF444444),
                animationSpec = androidx.compose.animation.core.tween(200),
                label = "submitBackground"
            )
            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = progress,
                animationSpec = androidx.compose.animation.core.tween(300),
                label = "uploadProgress"
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
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
                    }
            ) {
                if (uploading) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(22.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .background(Color.White.copy(alpha = 0.18f))
                        )
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "上传中 ${(progress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("上传投稿", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, color = appTextMuted(), fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (LocalAppDark.current) Color(0xFF262626) else Color(0xFFF4F4F5))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            textStyle = TextStyle(color = appText(), fontSize = 13.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BiliAccent)
        )
    }
}
