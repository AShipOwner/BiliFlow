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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    Box(Modifier.fillMaxSize().background(BiliBackground)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 52.dp, bottom = 56.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(rooms, key = { it.roomId }) { room ->
                LiveCard(room = room, modifier = Modifier.animateItem(), onClick = { onOpenLive(room) })
            }
        }

        if (loading) {
            CircularProgressIndicator(
                color = BiliAccent,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        } else if (error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("直播加载失败", color = Color.White, fontSize = 15.sp)
                TextButton(onClick = { retry++ }) { Text("重试", color = BiliAccent) }
            }
        } else if (rooms.isEmpty()) {
            Text(
                text = "暂无直播推荐",
                color = BiliMuted,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center)
            )
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
            .clip(RoundedCornerShape(10.dp))
            .pressClickable(pressedScale = 0.96f) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(10.dp))
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BiliAccent.copy(alpha = 0.9f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(Modifier.width(3.dp))
                Text("直播中", color = Color.White, fontSize = 9.sp)
            }
            Text(
                text = "${formatCount(room.online)} 人气",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = room.title,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(
                url = room.face,
                contentDescription = room.uname,
                modifier = Modifier.size(18.dp).clip(CircleShape),
                targetWidth = 64
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = room.uname,
                color = BiliMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (room.area.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = room.area,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

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
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(30.dp)
            )
        }
        if (error != null) {
            Text(
                text = error.orEmpty(),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp).noRippleClickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = room.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${room.uname} · ${room.area}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
