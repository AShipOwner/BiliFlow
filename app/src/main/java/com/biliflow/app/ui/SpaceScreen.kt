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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    var following by remember(mid) { mutableStateOf(false) }
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                AppEmptyState(
                    title = if (error == null) "还没有作品" else "作品暂时没有加载出来",
                    description = error ?: "这个空间还没有公开视频",
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                )
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
                .height(208.dp)
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(appChip())
        ) {
            if (banner.isNotBlank()) {
                RemoteImage(
                    url = banner,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 1080,
                    placeholder = appChip()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.28f))
                        )
                    )
                )
            }
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = Color.White,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.28f), CircleShape)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (-36).dp)
                .clip(RoundedCornerShape(20.dp))
                .background(appSurfaceRaised())
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(appChip())
                ) {
                    RemoteImage(
                        url = card?.face.orEmpty(),
                        contentDescription = card?.name,
                        modifier = Modifier.fillMaxSize(),
                        targetWidth = 280
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = card?.name.orEmpty().ifBlank { "用户 $fallbackMid" },
                        style = MaterialTheme.typography.titleLarge,
                        color = appText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "UID $fallbackMid",
                        style = MaterialTheme.typography.bodySmall,
                        color = appTextMuted()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                SpaceStat("获赞", card?.likes ?: 0L)
                SpaceStat("关注", (card?.following ?: 0).toLong())
                SpaceStat("粉丝", (card?.follower ?: 0).toLong())
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = card?.sign.orEmpty().ifBlank { "这个人很神秘，什么都没有写" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (card?.sign.isNullOrBlank()) appTextMuted() else appText()
            )
            Spacer(Modifier.height(18.dp))
            val followBackground by animateColorAsState(
                targetValue = if (following) appAccentSoft() else BiliAccent,
                animationSpec = tween(200),
                label = "spaceFollowBackground"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(followBackground)
                        .pressClickable(enabled = !busy, pressedScale = 0.96f) { onToggleFollow() },
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            color = if (following) BiliAccent else Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Crossfade(
                            targetState = following,
                            animationSpec = tween(180),
                            label = "spaceFollowLabel"
                        ) { isFollowing ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isFollowing) Icons.Filled.Check else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isFollowing) BiliAccent else Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = if (isFollowing) "已关注" else "关注",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isFollowing) BiliAccent else Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                AppSecondaryButton(
                    text = "私信",
                    onClick = onMessage,
                    modifier = Modifier.width(104.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "作品",
                    style = MaterialTheme.typography.titleSmall,
                    color = appText()
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${card?.archive ?: 0}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BiliAccent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(appAccentSoft())
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SpaceStat(label: String, value: Long) {
    Column {
        Text(
            text = formatCount(value),
            style = MaterialTheme.typography.titleSmall,
            color = appText()
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = appTextMuted())
    }
}

@Composable
private fun SpaceWorkCell(video: VideoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.pressClickable(pressedScale = 0.96f) { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(12.dp))
                .background(appChip())
        ) {
            RemoteImage(
                url = video.cover,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 300,
                placeholder = appChip()
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))
                    )
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = formatCount(video.view),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodySmall,
            color = appText(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp)
        )
    }
}
