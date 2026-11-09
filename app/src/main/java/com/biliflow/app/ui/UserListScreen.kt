package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.FanItem
import com.biliflow.app.data.FollowUser
import kotlinx.coroutines.launch

/** 关注 / 粉丝 列表（从「我的」页统计数字进入）。mode: 1=关注 2=粉丝 */
@Composable
fun UserListScreen(
    mode: Int,
    onOpenSpace: (Long) -> Unit,
    onBack: () -> Unit
) {
    var users by remember(mode) { mutableStateOf<List<FollowUser>>(emptyList()) }
    var fans by remember(mode) { mutableStateOf<List<FanItem>>(emptyList()) }
    var page by remember(mode) { mutableIntStateOf(1) }
    var loading by remember(mode) { mutableStateOf(true) }
    var end by remember(mode) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    BackHandler { onBack() }

    LaunchedEffect(mode) {
        loading = true
        if (mode == 2) {
            fans = runCatching { BiliApi.fansList(1) }.getOrDefault(emptyList())
            end = fans.isEmpty()
        } else {
            users = runCatching { BiliApi.followings(AuthManager.mid, 1) }.getOrDefault(emptyList())
            end = users.isEmpty()
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .screenEnter()
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).noRippleClickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = appText())
            }
            Text(
                text = if (mode == 2) "粉丝" else "关注",
                color = appText(),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            if (mode == 2) {
                items(fans, key = { it.mid }) { fan ->
                    UserRow(fan.mid, fan.name, fan.face, fan.sign, modifier = Modifier.animateItem()) { onOpenSpace(fan.mid) }
                    HorizontalDivider(color = appDivider(), modifier = Modifier.padding(start = 68.dp))
                }
                if (fans.isEmpty()) {
                    item { EmptyLine("还没有粉丝") }
                }
            } else {
                items(users, key = { it.mid }) { user ->
                    UserRow(
                        user.mid,
                        user.name,
                        user.face,
                        if (user.mutual) "互相关注" else "",
                        modifier = Modifier.animateItem()
                    ) { onOpenSpace(user.mid) }
                    HorizontalDivider(color = appDivider(), modifier = Modifier.padding(start = 68.dp))
                }
                if (users.isEmpty()) {
                    item { EmptyLine("还没有关注的人") }
                }
                if (!end && users.isNotEmpty()) {
                    item {
                        Text(
                            text = "加载更多",
                            color = appTextMuted(),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    scope.launch {
                                        val next = page + 1
                                        val more = runCatching { BiliApi.followings(AuthManager.mid, next) }
                                            .getOrDefault(emptyList())
                                        if (more.isEmpty()) end = true else {
                                            users = users + more
                                            page = next
                                        }
                                    }
                                }
                                .padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    mid: Long,
    name: String,
    face: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .pressClickable(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (LocalAppDark.current) Color(0xFF262626) else Color(0xFFEDEDEF))
        ) {
            RemoteImage(
                url = face,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                targetWidth = 120
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { "用户 $mid" },
                color = appText(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = appTextMuted(), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Text(text, color = appTextMuted(), fontSize = 13.sp)
    }
}
