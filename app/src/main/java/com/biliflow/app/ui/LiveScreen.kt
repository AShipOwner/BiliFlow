package com.biliflow.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.LiveRoom
import kotlinx.coroutines.CancellationException

@Composable
fun LiveScreen(onOpenLive: (LiveRoom) -> Unit) {
    var rooms by remember { mutableStateOf<List<LiveRoom>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(retry) {
        loading = true
        error = null
        try {
            rooms = BiliApi.liveRooms()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "直播加载失败"
        } finally {
            loading = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(appBackground())
            .statusBarsPadding()
            .padding(top = 60.dp, bottom = 64.dp)
    ) {
        when {
            loading && rooms.isEmpty() -> LiveLoadingGrid()

            error != null -> AppErrorState(
                message = error.orEmpty().ifBlank { "直播加载失败，请检查网络后重试" },
                onRetry = { retry++ },
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )

            rooms.isEmpty() -> AppEmptyState(
                title = "暂时没有直播推荐",
                description = "稍后再来看看，或刷新获取新的直播间",
                icon = Icons.Outlined.LiveTv,
                actionLabel = "刷新",
                onAction = { retry++ },
                modifier = Modifier.align(Alignment.Center).fillMaxWidth()
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(bottom = 2.dp)) {
                        Text(
                            text = "热门直播",
                            color = appText(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "发现正在发生的精彩",
                            color = appTextMuted(),
                            fontSize = 12.sp
                        )
                    }
                }
                items(rooms, key = { it.roomId }) { room ->
                    LiveCard(
                        room = room,
                        modifier = Modifier.animateItem(),
                        onClick = { onOpenLive(room) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveLoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SkeletonBox(Modifier.width(112.dp).height(26.dp))
                Spacer(Modifier.height(8.dp))
                SkeletonBox(Modifier.width(156.dp).height(14.dp))
            }
        }
        items(6) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(appSurface())
            ) {
                SkeletonBox(
                    Modifier.fillMaxWidth().aspectRatio(1.45f),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                Column(Modifier.padding(12.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(15.dp))
                    Spacer(Modifier.height(7.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.72f).height(15.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBox(Modifier.size(24.dp), CircleShape)
                        Spacer(Modifier.width(8.dp))
                        SkeletonBox(Modifier.weight(1f).height(11.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCard(room: LiveRoom, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveDot"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(appSurface())
            .pressClickable(pressedScale = 0.96f) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.45f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            RemoteImage(
                url = room.cover,
                contentDescription = room.title,
                modifier = Modifier.fillMaxSize(),
                targetWidth = 480
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BiliAccent)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "直播中",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "${formatCount(room.online)} 人气",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = room.title,
                color = appText(),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 40.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(
                    url = room.face,
                    contentDescription = room.uname,
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                    targetWidth = 64
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = room.uname,
                    color = appTextMuted(),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (room.area.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = room.area,
                        color = appTextMuted(),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.65f, fill = false)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun LivePlayerScreen(room: LiveRoom, onBack: () -> Unit) {
    val player = rememberBiliPlayer()
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) ready = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(room.roomId) {
        error = null
        ready = false
        try {
            val urls = BiliApi.livePlayUrl(room.roomId)
            val url = urls.firstOrNull()
            if (url.isNullOrBlank()) {
                error = "暂无可用直播流"
                return@LaunchedEffect
            }
            val factory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://live.bilibili.com/",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                    )
                )
                .setAllowCrossProtocolRedirects(true)
            val mediaItem = MediaItem.fromUri(url)
            val source = if (url.contains(".m3u8")) {
                HlsMediaSource.Factory(factory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(factory).createMediaSource(mediaItem)
            }
            player.setMediaSource(source)
            player.prepare()
            player.play()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            error = exception.message ?: "直播播放失败"
        }
    }

    PlayerLifecycle(player, userPaused = false)

    Box(Modifier.fillMaxSize().background(Color.Black).screenEnter(slideFromRight = false, scaleFrom = 0.96f)) {
        BiliPlayerSurface(player = player, modifier = Modifier.fillMaxSize())

        if (!ready && error == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoadingDots(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("正在连接直播", color = Color.White, fontSize = 13.sp)
            }
        }
        if (error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE6202229))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "直播暂时无法播放",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(18.dp))
                AppPrimaryButton(text = "返回直播列表", onClick = onBack)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(BiliAccent)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = room.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOf(room.uname, room.area).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
