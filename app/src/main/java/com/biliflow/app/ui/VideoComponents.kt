package com.biliflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.R
import com.biliflow.app.data.DanmakuItem
import com.biliflow.app.data.SocialState
import com.biliflow.app.data.VideoItem
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun FeedPage(
    item: VideoItem,
    social: SocialState,
    showVideo: Boolean,
    playerReady: Boolean,
    playerError: String?,
    paused: Boolean,
    progress: Float,
    danmaku: List<DanmakuItem>,
    danmakuEnabled: Boolean,
    danmakuPosition: androidx.compose.runtime.State<Long>,
    loggedIn: Boolean,
    interactive: Boolean,
    onTogglePlay: () -> Unit,
    onLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onComment: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onFollow: () -> Unit,
    onRetry: () -> Unit,
    onOpenOwner: () -> Unit,
    onRequireLogin: () -> Unit,
    onSendDanmaku: (String) -> Unit,
    recallAvailable: Boolean = false,
    onRecall: () -> Unit = {},
    speed: Float = 1f,
    qualityText: String = "",
    fillScreen: Boolean = false,
    onOpenSpeed: () -> Unit = {},
    onOpenQuality: () -> Unit = {},
    onToggleFill: () -> Unit = {},
    boosting: Boolean = false,
    onBoost: (Boolean) -> Unit = {},
    onToggleDanmaku: () -> Unit = {},
    durationMs: Long = 0L,
    onSeek: (Float) -> Unit = {}
) {
    val density = LocalDensity.current
    var hearts by remember { mutableStateOf<List<TapHeart>>(emptyList()) }
    var heartId by remember { mutableStateOf(0L) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !showVideo || !playerReady,
            enter = EnterTransition.None,
            exit = fadeOut(tween(320)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                RemoteImage(
                    url = item.cover,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    targetWidth = 720
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.32f),
                                0.46f to Color.Black.copy(alpha = 0.04f),
                                1f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                )
            }
        }
        if (showVideo && playerReady) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            0.78f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.9f)
                        )
                    )
            )
        }

        if (danmakuEnabled && showVideo) {
            DanmakuOverlay(
                items = danmaku,
                position = danmakuPosition,
                enabled = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 52.dp, bottom = 148.dp)
            )
        }

        if (interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(item.bvid) {
                        detectTapGestures(
                            onTap = { onTogglePlay() },
                            onDoubleTap = { tapOffset ->
                                onDoubleTapLike()
                                heartId += 1L
                                hearts = hearts + TapHeart(
                                    id = heartId,
                                    offset = tapOffset,
                                    rotation = Random.nextFloat() * 36f - 18f,
                                    sizeScale = 0.9f + Random.nextFloat() * 0.25f
                                )
                            }
                        )
                    }
            )
        }

        if (interactive) {
            // 长按右侧 2x：按下 220ms 后提速，松手恢复
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(120.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val quickRelease = withTimeoutOrNull(
                                viewConfiguration.longPressTimeoutMillis
                            ) {
                                waitForUpOrCancellation()
                                true
                            }
                            if (quickRelease == null) {
                                onBoost(true)
                                waitForUpOrCancellation()
                                onBoost(false)
                            }
                        }
                    }
            )
        }

        ActionRail(
            item = item,
            social = social,
            enabled = interactive,
            playing = playerReady && !paused,
            onLike = onLike,
            onComment = onComment,
            onCoin = onCoin,
            onFavorite = onFavorite,
            onShare = onShare,
            onFollow = onFollow,
            onOpenOwner = onOpenOwner,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = 100.dp)
        )

        AnimatedVisibility(
            visible = boosting,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 62.dp)
        ) {
            Text(
                text = "2.0x 倍速中",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (interactive) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerSideTool(
                    label = if (speed == 1f) "倍速" else speedLabelOf(speed),
                    highlighted = speed != 1f,
                    onClick = onOpenSpeed
                )
                if (qualityText.isNotBlank()) {
                    PlayerSideTool(label = qualityText, onClick = onOpenQuality)
                }
                PlayerSideTool(
                    label = if (fillScreen) "铺满" else "完整",
                    onClick = onToggleFill
                )
            }
        }

        VideoInfo(
            item = item,
            enabled = interactive,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 84.dp, bottom = 98.dp)
        )

        if (interactive && recallAvailable) {
            Text(
                text = "撤回弹幕",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 56.dp, end = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .noRippleClickable { onRecall() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

        if (interactive && danmakuEnabled && playerError == null) {
            DanmakuBar(
                enabled = true,
                danmakuOn = danmakuEnabled,
                onToggleDanmaku = onToggleDanmaku,
                loggedIn = loggedIn,
                onRequireLogin = onRequireLogin,
                onSend = onSendDanmaku,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 64.dp)
            )
        }

        if (interactive) {
            SeekableProgressBar(
                progress = progress,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 45.dp)
            )
        }

        AnimatedVisibility(
            visible = interactive && paused && playerReady && playerError == null,
            enter = fadeIn(tween(150)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.7f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = "暂停",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = interactive && !playerReady && playerError == null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(30.dp)
            )
        }

        AnimatedVisibility(
            visible = interactive && playerError != null,
            enter = fadeIn(tween(180)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.92f),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "播放失败",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = playerError.orEmpty(),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BiliAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text("重试")
                }
            }
        }

        hearts.forEach { heart ->
            androidx.compose.runtime.key(heart.id) {
                TapHeartView(
                    heart = heart,
                    heartSizePx = with(density) { 108.dp.toPx() },
                    onDone = { hearts = hearts.filterNot { it.id == heart.id } }
                )
            }
        }
    }
}

private data class TapHeart(
    val id: Long,
    val offset: Offset,
    val rotation: Float,
    val sizeScale: Float
)

/** 双击飘心：弹簧入场 → 上飘 → 淡出，支持连续多击叠加。 */
@Composable
private fun TapHeartView(
    heart: TapHeart,
    heartSizePx: Float,
    onDone: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(0f) }
    LaunchedEffect(heart.id) {
        launch {
            scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 380f))
        }
        progress.animateTo(1f, tween(820, easing = LinearEasing))
        onDone()
    }
    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = BiliAccent,
        modifier = Modifier
            .offset {
                IntOffset(
                    (heart.offset.x - heartSizePx / 2f).roundToInt(),
                    (heart.offset.y - heartSizePx / 2f - progress.value * heartSizePx * 0.55f).roundToInt()
                )
            }
            .size(108.dp)
            .graphicsLayer {
                val p = progress.value
                val s = scale.value * heart.sizeScale
                scaleX = s
                scaleY = s
                alpha = (1f - ((p - 0.55f) / 0.45f)).coerceIn(0f, 1f)
                rotationZ = heart.rotation + p * 8f
            }
    )
}

/** 点赞粒子爆发：liked 翻转为 true 时向外散开一圈小粒子。 */
@Composable
private fun LikeBurst(
    trigger: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger && !previous) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
        }
        previous = trigger
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val count = 7
        val travel = size.minDimension * 0.52f
        repeat(count) { i ->
            val angle = (i * 360f / count + 20f) * PI.toFloat() / 180f
            val distance = travel * (0.25f + 0.75f * p)
            drawCircle(
                color = BiliAccent.copy(alpha = (1f - p) * 0.95f),
                radius = size.minDimension * 0.055f * (1f - p * 0.45f),
                center = center + Offset(cos(angle) * distance, sin(angle) * distance)
            )
        }
    }
}

/** 底部进度条：平时细线匀速动画，按住变粗可拖动定位，松手 seek。 */
@Composable
private fun SeekableProgressBar(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(0f) }
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        if (!dragging) {
            // 循环回绕时直接跳变，其余情况在两次采样间匀速补齐
            if (progress < animated.value - 0.5f) animated.snapTo(progress)
            else animated.animateTo(progress.coerceIn(0f, 1f), tween(480, easing = LinearEasing))
        }
    }
    val shown = (if (dragging) dragFraction else animated.value).coerceIn(0f, 1f)
    val barHeight by animateDpAsState(
        targetValue = if (dragging) 4.dp else 2.dp,
        animationSpec = tween(160),
        label = "barHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(durationMs) {
                if (durationMs <= 0L) return@pointerInput
                detectTapGestures(onTap = { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                })
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0L) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        scope.launch { animated.snapTo(dragFraction) }
                        onSeek(dragFraction)
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (dragging) 0.32f else 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(if (dragging) BiliAccent else Color.White)
            )
        }
        if (dragging && widthPx > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            (widthPx * shown - with(density) { 7.dp.toPx() }).roundToInt().coerceAtLeast(0),
                            0
                        )
                    }
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Text(
                text = "${formatDuration((dragFraction * durationMs / 1000).toInt())} / ${formatDuration((durationMs / 1000).toInt())}",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = DouyinNumber,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-34).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ActionRail(
    item: VideoItem,
    social: SocialState,
    enabled: Boolean,
    playing: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onCoin: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onFollow: () -> Unit,
    onOpenOwner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val likePop = rememberPopScale(social.liked)
    val favoritePop = rememberPopScale(social.favorite)
    val favoriteTint by animateColorAsState(
        targetValue = if (social.favorite) Color(0xFFFFD54F) else Color.White,
        animationSpec = tween(200),
        label = "favoriteTint"
    )
    val coinTint by animateColorAsState(
        targetValue = if (social.coinCount + social.coinDelta > 0) BiliAccent else Color.White,
        animationSpec = tween(200),
        label = "coinTint"
    )
    val followBackground by animateColorAsState(
        targetValue = if (social.following) Color.White else BiliAccent,
        animationSpec = tween(220),
        label = "followBackground"
    )
    val likeCount = item.like + social.likeDelta
    val favoriteCount = item.favorite + social.favoriteDelta
    val coinCount = (item.coin + social.coinDelta).coerceAtLeast(0L)

    Column(
        modifier = modifier.width(68.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            RemoteImage(
                url = item.ownerFace,
                contentDescription = item.ownerName,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .pressClickable(pressedScale = 0.92f) { android.util.Log.d("BiliFlow", "avatar tap mid=${item.ownerMid}"); onOpenOwner() },
                targetWidth = 96,
                placeholder = Color(0xFF2A2A2A)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(followBackground)
                    .pressClickable(enabled = enabled, pressedScale = 0.8f) { onFollow() },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = social.following,
                    animationSpec = tween(200),
                    label = "followIcon"
                ) { following ->
                    Icon(
                        imageVector = if (following) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = if (following) "已关注" else "关注",
                        tint = if (following) Color.Black else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        ActionButton(
            label = formatCount(likeCount),
            enabled = enabled,
            onClick = onLike
        ) {
            Box(Modifier.requiredSize(44.dp), contentAlignment = Alignment.Center) {
                LikeBurst(
                    trigger = social.liked,
                    modifier = Modifier.matchParentSize()
                )
                Icon(
                    imageVector = if (social.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "点赞",
                    tint = if (social.liked) BiliAccent else Color.White,
                    modifier = Modifier
                        .size(31.dp)
                        .graphicsLayer {
                            scaleX = likePop
                            scaleY = likePop
                        }
                )
            }
        }

        ActionButton(
            label = if (item.reply > 0L) formatCount(item.reply) else "评论",
            enabled = enabled,
            onClick = onComment
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_comment),
                contentDescription = "评论",
                tint = Color.White,
                modifier = Modifier.size(29.dp)
            )
        }

        ActionButton(
            label = if (coinCount > 0L) formatCount(coinCount) else "投币",
            enabled = enabled,
            onClick = onCoin
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_coin),
                contentDescription = "投币",
                tint = coinTint,
                modifier = Modifier.size(29.dp)
            )
        }

        ActionButton(
            label = if (favoriteCount > 0L) formatCount(favoriteCount) else "收藏",
            enabled = enabled,
            onClick = onFavorite
        ) {
            Icon(
                imageVector = if (social.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (social.favorite) "取消收藏" else "收藏",
                tint = favoriteTint,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = favoritePop
                        scaleY = favoritePop
                    }
            )
        }

        ActionButton(
            label = if (item.share > 0L) formatCount(item.share) else "分享",
            enabled = enabled,
            onClick = onShare
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "分享",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        MusicDisc(item = item, spinning = playing)
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .width(64.dp)
            .pressClickable(enabled = enabled, pressedScale = 0.88f) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(31.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.height(2.dp))
        AnimatedSwapText(
            text = label,
            style = TextStyle(
                color = Color.White,
                fontSize = DouyinType.caption,
                fontWeight = FontWeight.Medium,
                fontFamily = DouyinNumber,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.7f),
                    offset = Offset(0f, 1f),
                    blurRadius = 4f
                )
            )
        )
    }
}

@Composable
private fun MusicDisc(item: VideoItem, spinning: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        // 暂停时停在当前角度，恢复时继续转
        while (spinning) {
            rotation.animateTo(rotation.value + 360f, tween(9000, easing = LinearEasing))
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(CircleShape)
            .background(Color(0xFF171717))
            .border(5.dp, Color(0xFF262626), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        RemoteImage(
            url = item.cover,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            targetWidth = 96,
            placeholder = Color(0xFF262626)
        )
        Icon(
            painter = painterResource(R.drawable.ic_music),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun VideoInfo(item: VideoItem, enabled: Boolean, modifier: Modifier = Modifier) {
    var expanded by remember(item.bvid) { mutableStateOf(false) }
    val shadow = Shadow(
        color = Color.Black.copy(alpha = 0.68f),
        offset = Offset(0f, 1f),
        blurRadius = 5f
    )

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "@${item.ownerName}",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = shadow
                ),
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.category.isNotBlank()) {
                Spacer(Modifier.width(7.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.category,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.desc.ifBlank { item.title },
            color = Color.White,
            fontSize = 14.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = shadow, lineHeight = 20.sp),
            modifier = Modifier
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 420f))
                .noRippleClickable(enabled = enabled) {
                    expanded = !expanded
                }
        )

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_music),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "视频原声 · ${item.ownerName}",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = shadow)
            )
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text = "哔哩哔哩 · ${item.category.ifBlank { "视频" }} · ${formatCount(item.view)} 播放",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = DouyinType.bodySm,
            fontFamily = DouyinNumberCondensed,
            maxLines = 1,
            style = TextStyle(shadow = shadow)
        )
    }
}

@Composable
private fun PlayerSideTool(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (highlighted) BiliYellow else Color.White,
        animationSpec = tween(200),
        label = "sideToolColor"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .pressClickable(pressedScale = 0.88f) { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        AnimatedSwapText(
            text = label,
            style = TextStyle(
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}
