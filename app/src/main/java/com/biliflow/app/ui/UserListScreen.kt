package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    var loadingMore by remember(mode) { mutableStateOf(false) }
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
            .navigationBarsPadding()
    ) {
        AppPageHeader(
            title = if (mode == 2) "粉丝" else "关注",
            subtitle = when {
                loading -> "正在同步列表"
                mode == 2 -> "${fans.size} 位粉丝"
                else -> "${users.size} 个关注"
            },
            onBack = onBack
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            if (mode == 2) {
                items(fans, key = { it.mid }) { fan ->
                    UserRow(
                        fan.mid, fan.name, fan.face, fan.sign.ifBlank { "这位用户很神秘" },
                        modifier = Modifier.animateItem().padding(vertical = 4.dp)
                    ) { onOpenSpace(fan.mid) }
                }
                if (fans.isEmpty()) {
                    item {
                        AppEmptyState(
                            title = "还没有粉丝",
                            description = "新的关注者会出现在这里",
                            modifier = Modifier.fillParentMaxHeight(0.72f)
                        )
                    }
                }
            } else {
                items(users, key = { it.mid }) { user ->
                    UserRow(
                        user.mid,
                        user.name,
                        user.face,
                        if (user.mutual) "互相关注" else "前往 TA 的主页",
                        emphasized = user.mutual,
                        modifier = Modifier.animateItem().padding(vertical = 4.dp)
                    ) { onOpenSpace(user.mid) }
                }
                if (users.isEmpty()) {
                    item {
                        AppEmptyState(
                            title = "还没有关注的人",
                            description = "发现感兴趣的用户后，可以在这里快速找到 TA",
                            modifier = Modifier.fillParentMaxHeight(0.72f)
                        )
                    }
                }
                if (!end && users.isNotEmpty()) {
                    item {
                        AppSecondaryButton(
                            text = if (loadingMore) "正在加载…" else "加载更多",
                            enabled = !loadingMore,
                            onClick = {
                                if (loadingMore) return@AppSecondaryButton
                                scope.launch {
                                    loadingMore = true
                                    val next = page + 1
                                    val more = runCatching { BiliApi.followings(AuthManager.mid, next) }
                                        .getOrDefault(emptyList())
                                    if (more.isEmpty()) end = true else {
                                        users = users + more
                                        page = next
                                    }
                                    loadingMore = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    }
                } else if (end && users.isNotEmpty()) {
                    item {
                        Text(
                            text = "已经到底啦",
                            style = MaterialTheme.typography.bodySmall,
                            color = appTextMuted(),
                            modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(),
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
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appSurfaceRaised())
            .pressClickable(pressedScale = 0.97f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(appChip())
        ) {
            RemoteImage(
                url = face,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                targetWidth = 160
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { "用户 $mid" },
                style = MaterialTheme.typography.titleSmall,
                color = appText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (emphasized) BiliAccent else appTextMuted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = appTextMuted(),
            modifier = Modifier.size(22.dp)
        )
    }
}
