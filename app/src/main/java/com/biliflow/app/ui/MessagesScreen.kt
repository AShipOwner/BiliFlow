package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.ChatMessage
import com.biliflow.app.data.FanItem
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.MessageItem
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.VideoItem
import com.biliflow.app.data.WhisperSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(
    onOpenVideo: (VideoItem) -> Unit = {},
    onOpenSpace: (Long) -> Unit = {},
    onOpenWeb: (String) -> Unit = {}
) {
    val authState by AuthManager.state.collectAsStateCompat()
    val loggedIn = authState is AuthState.LoggedIn
    val context = LocalContext.current

    var category by remember { mutableStateOf("reply") }
    var messages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var systemMessages by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var fans by remember { mutableStateOf<List<FanItem>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<WhisperSession>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var chatTarget by remember { mutableStateOf<WhisperSession?>(null) }

    LaunchedEffect(category, loggedIn, reload) {
        if (!loggedIn) return@LaunchedEffect
        loading = true
        error = null
        try {
            when (category) {
                "reply", "at" -> messages = BiliApi.messageFeed(category)
                "like" -> messages = BiliApi.likeFeed()
                "fans" -> fans = BiliApi.fansList(1)
                "whisper" -> sessions = BiliApi.whisperSessions(1)
                "system" -> systemMessages = BiliApi.systemMessages()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(appBackground())) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
        ) {
            Text(
                text = "消息",
                color = appText(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MessageCategory("回复", Icons.Filled.ChatBubble, category == "reply") { category = "reply" }
                MessageCategory("赞", Icons.Filled.Favorite, category == "like") { category = "like" }
                MessageCategory("@", Icons.Filled.AlternateEmail, category == "at") { category = "at" }
                MessageCategory("粉丝", Icons.Filled.People, category == "fans") { category = "fans" }
                MessageCategory("私信", Icons.Filled.Email, category == "whisper") { category = "whisper" }
                MessageCategory("系统", Icons.Filled.Notifications, category == "system") { category = "system" }
            }
            HorizontalDivider(color = appDivider(), modifier = Modifier.padding(top = 6.dp))

            when {
                !loggedIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("登录后查看消息", color = appText(), fontSize = 16.sp)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { LoginPrompt.show() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BiliAccent,
                                contentColor = Color.White
                            )
                        ) { Text("扫码登录") }
                    }
                }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = BiliAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }

                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = appTextMuted(), fontSize = 12.sp)
                        TextButton(onClick = { reload++ }) { Text("重试", color = BiliAccent) }
                    }
                }

                category == "fans" -> {
                    if (fans.isEmpty()) {
                        EmptyHint("暂无粉丝")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(fans, key = { it.mid }) { fan ->
                                FanRow(fan) { onOpenSpace(fan.mid) }
                                HorizontalDivider(color = appDivider(), modifier = Modifier.padding(start = 70.dp))
                            }
                        }
                    }
                }

                category == "whisper" -> {
                    if (sessions.isEmpty()) {
                        EmptyHint("暂无私信")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(sessions, key = { it.talkerId }) { session ->
                                SessionRow(session) { chatTarget = session }
                                HorizontalDivider(color = appDivider(), modifier = Modifier.padding(start = 70.dp))
                            }
                        }
                    }
                }

                category == "system" -> {
                    if (systemMessages.isEmpty()) {
                        EmptyHint("暂无系统消息")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(systemMessages, key = { it.id }) { message ->
                                SystemRow(message) {
                                    if (message.uri.startsWith("http")) {
                                        onOpenWeb(message.uri)
                                    } else {
                                        openMessageTarget(message, onOpenVideo, onOpenSpace)
                                    }
                                }
                                HorizontalDivider(color = appDivider(), modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }

                else -> {
                    if (messages.isEmpty()) {
                        EmptyHint("暂无消息")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(messages, key = { it.id }) { message ->
                                MessageRow(message) {
                                    openMessageTarget(message, onOpenVideo, onOpenSpace)
                                }
                                HorizontalDivider(color = appDivider(), modifier = Modifier.padding(start = 70.dp))
                            }
                        }
                    }
                }
            }
        }

        val target = chatTarget
        if (target != null) {
            ChatScreen(
                session = target,
                onOpenSpace = onOpenSpace,
                onBack = { chatTarget = null }
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = appTextMuted(), fontSize = 13.sp)
    }
}

@Composable
private fun MessageCategory(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) BiliAccent.copy(alpha = 0.18f) else appSurface(),
        animationSpec = tween(200),
        label = "categoryBackground"
    )
    val tint by animateColorAsState(
        targetValue = if (selected) BiliAccent else appText(),
        animationSpec = tween(200),
        label = "categoryTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = BiliMotion.popSpring(),
        label = "categoryScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pressClickable(pressedScale = 0.88f) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = if (selected) appText() else appTextMuted(),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun MessageRow(message: MessageItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            RemoteImage(
                url = message.face,
                contentDescription = message.nickname,
                modifier = Modifier.size(46.dp).clip(CircleShape),
                targetWidth = 120
            )
            if (message.isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(BiliAccent)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.nickname,
                    color = appText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(formatTimeAgo(message.time), color = appTextMuted(), fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = message.content,
                color = appTextMuted(),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (message.title.isNotBlank() && message.content != message.title) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.title,
                    color = appText().copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FanRow(fan: FanItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteImage(
            url = fan.face,
            contentDescription = fan.name,
            modifier = Modifier.size(46.dp).clip(CircleShape),
            targetWidth = 120
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(fan.name, color = appText(), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(
                text = fan.sign.ifBlank { "这位用户很神秘" },
                color = appTextMuted(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionRow(session: WhisperSession, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            RemoteImage(
                url = session.face,
                contentDescription = session.name,
                modifier = Modifier.size(46.dp).clip(CircleShape),
                targetWidth = 120
            )
            if (session.unread > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(BiliAccent)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (session.unread > 99) "99+" else session.unread.toString(),
                        color = appText(),
                        fontSize = 9.sp
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.name,
                    color = appText(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(formatTimeAgo(session.ts), color = appTextMuted(), fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = prettyMessage(session.lastMsg).ifBlank { "[消息]" },
                color = appTextMuted(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatScreen(
    session: WhisperSession,
    onOpenSpace: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(session.talkerId) {
        loading = true
        try {
            messages = BiliApi.chatMessages(session.talkerId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            messages = emptyList()
        } finally {
            loading = false
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }
    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .screenEnter()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).pressClickable(pressedScale = 0.85f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = appText())
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .noRippleClickable { onOpenSpace(session.talkerId) }
            ) {
                RemoteImage(
                    url = session.face,
                    contentDescription = session.name,
                    modifier = Modifier.fillMaxSize(),
                    targetWidth = 120
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = session.name,
                color = appText(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = appDivider())

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    messages,
                    key = { index, message -> "$index-${message.ts}-${message.content.hashCode()}" }
                ) { _, message ->
                    Row(
                        Modifier.fillMaxWidth().animateItem(),
                        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (message.isMine) BiliAccent else appSurface())
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = prettyMessage(message.content),
                                color = if (message.isMine) Color.White else appText(),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = appDivider())
        ChatComposer(
            onSend = { text ->
                val optimistic = ChatMessage(
                    senderUid = 0L,
                    content = text,
                    ts = System.currentTimeMillis() / 1000L,
                    isMine = true
                )
                messages = messages + optimistic
                scope.launch {
                    try {
                        BiliApi.sendWhisper(session.talkerId, text)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        messages = messages.filterNot { it === optimistic }
                        val msg = error.message ?: "发送失败"
                        if (error is BiliApi.BiliException && error.code == -101) {
                            LoginPrompt.show()
                        } else {
                            UiMessages.show(msg)
                        }
                    }
                }
            }
        )
    }
}

private val BV_REGEX = Regex("BV[0-9A-Za-z]{10}")
private val SPACE_REGEX = Regex("(?:space\\.bilibili\\.com/|bilibili://space/|bilibili://author/)(\\d+)")

/** 把消息里的 uri 解析成应用内目标，避免整条消息丢给浏览器。 */
internal fun openMessageTarget(
    message: MessageItem,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenSpace: (Long) -> Unit
) {
    val uri = message.uri
    val bv = BV_REGEX.find(uri)?.value
    if (!bv.isNullOrBlank()) {
        onOpenVideo(VideoItem(bvid = bv, title = message.title, ownerName = message.nickname, ownerMid = message.mid))
        return
    }
    val mid = SPACE_REGEX.find(uri)?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: message.mid.takeIf { it > 0L }
    if (mid != null && mid > 0L) {
        onOpenSpace(mid)
        return
    }
    UiMessages.show("该条消息暂不支持应用内打开")
}


@Composable
private fun SystemRow(message: MessageItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .noRippleClickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFFE2C55).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Notifications, null, tint = Color(0xFFFE2C55), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = message.title.ifBlank { "系统通知" },
                color = appText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (message.content.isNotBlank() && message.content != message.title) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = message.content,
                    color = appTextMuted(),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (message.time > 0L) {
            Text(formatTimeAgo(message.time), color = appTextMuted(), fontSize = 10.sp)
        }
    }
}

@Composable
private fun ChatComposer(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it.take(500) },
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(appField())
                .padding(horizontal = 14.dp),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = appText(), fontSize = 13.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BiliAccent),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("发消息…", color = appTextMuted(), fontSize = 13.sp)
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "发送",
            color = if (text.isBlank()) appTextMuted() else BiliAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.noRippleClickable {
                val value = text.trim()
                if (value.isNotBlank()) {
                    onSend(value)
                    text = ""
                    focusManager.clearFocus()
                }
            }
        )
    }
}
