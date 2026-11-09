package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.UserHomeCard
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 用户主页：对齐抖音/参考图的版式（背景图 + 头像 + 获赞/关注/粉丝 + 关注按钮 + 作品网格）。 */
@Composable
fun SpaceScreen(
    mid: Long,
    onOpenVideo: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    var card by remember(mid) { mutableStateOf<UserHomeCard?>(null) }
    var videos by remember(mid) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var page by remember(mid) { mutableIntStateOf(2) }
    var loading by remember(mid) { mutableStateOf(true) }
    var loadingMore by remember(mid) { mutableStateOf(false) }
    var end by remember(mid) { mutableStateOf(false) }
    var error by remember(mid) { mutableStateOf<String?>(null) }
    var following by remember(mid) {
        mutableStateOf(AuthManager.isLoggedIn && AuthManager.mid > 0L)
    }
    var followingBusy by remember(mid) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    BackHandler { onBack() }

    LaunchedEffect(mid) {
        loading = true
        error = null
        card = runCatching { BiliApi.userHomeCard(mid) }.getOrNull()
        val first = runCatching { BiliApi.spaceVideos(mid, 1) }
        videos = first.getOrDefault(emptyList())
        end = videos.isEmpty()
        if (first.isFailure) error = first.exceptionOrNull()?.message ?: "加载失败"
        loading = false
    }

    LaunchedEffect(gridState, mid) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index ->
                if (loadingMore || end || videos.isEmpty()) return@collect
                if (index >= videos.size - 3) {
                    loadingMore = true
                    val next = page
                    val chunk = runCatching { BiliApi.spaceVideos(mid, next) }.getOrNull()
                    if (chunk.isNullOrEmpty()) {
                        end = true
                    } else {
                        val merged = (videos + chunk).distinctBy { it.bvid }
                        if (merged.size == videos.size) end = true else page = next + 1
                        videos = merged
                    }
                    loadingMore = false
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .background(appBackground())
            .screenEnter()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SpaceHeader(
                card = card,
                fallbackMid = mid,
                following = following,
                busy = followingBusy,
                onBack = onBack,
                onToggleFollow = {
                    if (!AuthManager.ensureLogin()) return@SpaceHeader
                    if (followingBusy) return@SpaceHeader
                    scope.launch {
                        followingBusy = true
                        runCatching { BiliApi.followUser(mid, !following) }
                            .onSuccess {
                                following = !following
                                UiMessages.show(if (following) "已关注" else "已取消关注")
                            }
                            .onFailure { UiMessages.show(it.message ?: "操作失败") }
                        followingBusy = false
                    }
                },
                onMessage = { UiMessages.show("私信入口在「消息」页") }
            )
        }

        if (loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }

        if (!loading && videos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    Text(error ?: "还没有作品", color = appTextMuted(), fontSize = 13.sp)
                }
            }
        }

        items(videos, key = { it.bvid }) { video ->
            SpaceWorkCell(video, modifier = Modifier.animateItem()) { onOpenVideo(video) }
        }

        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BiliAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SpaceHeader(
    card: UserHomeCard?,
    fallbackMid: Long,
    following: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit
) {
    Column {
        val banner = card?.banner.orEmpty()
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(Color(0xFF3D7FC4))
        ) {
            if (banner.isNotBlank()) {
                RemoteImage(
                    url = banner,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    targetWidth = 1080,
                    placeholder = Color(0xFF3D7FC4)
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .pressClickable(pressedScale = 0.85f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }

            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2B3A44))
                ) {
                    RemoteImage(
                        url = card?.face.orEmpty(),
                        contentDescription = card?.name,
                        modifier = Modifier.fillMaxSize(),
                        targetWidth = 280
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = card?.name.orEmpty().ifBlank { "用户 $fallbackMid" },
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "UID：$fallbackMid",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(appSurface())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row {
                SpaceStat("获赞", card?.likes ?: 0L)
                SpaceStat("关注", (card?.following ?: 0).toLong())
                SpaceStat("粉丝", (card?.follower ?: 0).toLong())
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = card?.sign.orEmpty().ifBlank { "这个人很神秘，什么都没有写" },
                color = appText(),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(14.dp))
            val followBackground by animateColorAsState(
                targetValue = if (following) Color(0xFFE9E9EC) else BiliAccent,
                animationSpec = tween(220),
                label = "spaceFollowBackground"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(followBackground)
                        .pressClickable(enabled = !busy, pressedScale = 0.96f) { onToggleFollow() },
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = if (following) Color(0xFF161823) else Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Crossfade(
                            targetState = following,
                            animationSpec = tween(200),
                            label = "spaceFollowLabel"
                        ) { isFollowing ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isFollowing) Icons.Filled.Check else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFollowing) Color(0xFF161823) else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isFollowing) "已关注" else "+ 关注",
                                    color = if (isFollowing) Color(0xFF161823) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (LocalAppDark.current) Color(0xFF262626) else Color(0xFFF1F1F2))
                        .pressClickable(pressedScale = 0.88f) { onMessage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "私信", tint = appText(), modifier = Modifier.size(17.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "作品 ${card?.archive ?: 0}",
                    color = appText(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text("▾", color = appTextMuted(), fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SpaceStat(label: String, value: Long) {
    Row(
        Modifier.padding(end = 22.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = formatCount(value),
            color = appText(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DouyinNumber
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = appTextMuted(), fontSize = 11.sp)
    }
}

@Composable
private fun SpaceWorkCell(video: VideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(if (LocalAppDark.current) Color(0xFF1B1B1F) else Color(0xFFE9E9EC))
            .pressClickable(pressedScale = 0.95f) { onClick() }
    ) {
        RemoteImage(
            url = video.cover,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            targetWidth = 300,
            placeholder = if (LocalAppDark.current) Color(0xFF1B1B1F) else Color(0xFFE9E9EC)
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("♡", color = Color.White, fontSize = 10.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                text = formatCount(video.view),
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = DouyinNumber
            )
        }
    }
}
