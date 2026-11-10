package com.biliflow.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.DanmakuItem
import com.biliflow.app.data.FeedSource
import com.biliflow.app.data.LiveRoom
import com.biliflow.app.data.LocalStore
import com.biliflow.app.data.LoginPrompt
import com.biliflow.app.data.NetworkMonitor
import com.biliflow.app.data.QualityMode
import com.biliflow.app.data.SocialState
import com.biliflow.app.data.UiMessages
import com.biliflow.app.data.VideoItem
import com.biliflow.app.data.effectiveQualityQn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class HomeTab { LIVE, MALL, FOLLOW, RECOMMEND }

@Composable
fun HomeScreen(
    viewModel: FeedViewModel,
    homeTab: HomeTab,
    onHomeTabChange: (HomeTab) -> Unit,
    onSearch: () -> Unit,
    onOpenLive: (LiveRoom) -> Unit,
    onOpenSpace: (Long) -> Unit = {}
) {
    val authState by AuthManager.state.collectAsStateCompat()
    val loggedIn = authState is AuthState.LoggedIn
    val danmakuEnabled by LocalStore.danmakuEnabled.collectAsStateCompat()

    var commentItem by remember { mutableStateOf<VideoItem?>(null) }
    var coinItem by remember { mutableStateOf<VideoItem?>(null) }
    val context = LocalContext.current

    val source = if (homeTab == HomeTab.FOLLOW) FeedSource.FOLLOW else FeedSource.RECOMMEND
    val isVideoTab = homeTab == HomeTab.FOLLOW || homeTab == HomeTab.RECOMMEND

    LaunchedEffect(source, loggedIn, isVideoTab) {
        if (isVideoTab) viewModel.ensureLoaded(source)
    }

    val toggleDanmaku = {
        val next = !danmakuEnabled
        LocalStore.setDanmakuEnabled(next)
        UiMessages.show(if (next) "弹幕已开启" else "弹幕已关闭")
    }

    Box(Modifier.fillMaxSize().background(if (isVideoTab) BiliBackground else appBackground())) {
        AnimatedContent(
            targetState = homeTab,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "homeTab"
        ) { currentTab ->
            val tabSource = if (currentTab == HomeTab.FOLLOW) FeedSource.FOLLOW else FeedSource.RECOMMEND
            val tabState by viewModel.state(tabSource).collectAsStateCompat()
            when (currentTab) {
                HomeTab.LIVE -> LiveScreen(onOpenLive = onOpenLive)
                HomeTab.MALL -> MallScreen()
                HomeTab.FOLLOW -> {
                    if (!loggedIn) {
                        FeedLoginPrompt()
                    } else {
                        key(tabSource) {
                            FeedPager(
                                source = tabSource,
                                state = tabState,
                                viewModel = viewModel,
                                onComment = { commentItem = it },
                                onCoin = { item ->
                                    if (loggedIn) coinItem = item else LoginPrompt.show()
                                },
                                onOpenOwner = { onOpenSpace(it.ownerMid) },
                                onToggleDanmaku = toggleDanmaku
                            )
                        }
                    }
                }

                HomeTab.RECOMMEND -> {
                    key(tabSource) {
                        FeedPager(
                            source = tabSource,
                            state = tabState,
                            viewModel = viewModel,
                            onComment = { commentItem = it },
                            onCoin = { item ->
                                if (loggedIn) coinItem = item else LoginPrompt.show()
                            },
                            onOpenOwner = { onOpenSpace(it.ownerMid) },
                            onToggleDanmaku = toggleDanmaku
                        )
                    }
                }
            }
        }

        HomeTopBar(
            selected = homeTab,
            onSelect = onHomeTabChange,
            onSearch = onSearch,
            immersive = isVideoTab
        )
    }

    val sheetItem = commentItem
    if (sheetItem != null) {
        CommentsSheet(
            item = sheetItem,
            viewModel = viewModel,
            onDismiss = { commentItem = null },
            onOpenSpace = { mid ->
                commentItem = null
                onOpenSpace(mid)
            }
        )
    }

    val coin = coinItem
    if (coin != null) {
        CoinSheet(
            item = coin,
            user = (authState as? AuthState.LoggedIn)?.user,
            social = viewModel.socialOf(coin.bvid),
            onDismiss = { coinItem = null },
            onConfirm = { count, alsoLike ->
                viewModel.addCoin(coin, count, alsoLike)
                coinItem = null
            }
        )
    }
}

@Composable
private fun HomeTopBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    onSearch: () -> Unit,
    immersive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (immersive) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.66f), Color.Transparent)
                        )
                    )
                } else {
                    Modifier.background(appBackground())
                }
            )
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 10.dp)
    ) {
        val centers = remember { mutableStateMapOf<HomeTab, Float>() }
        val targetX = centers[selected]
        val indicatorX by animateFloatAsState(
            targetValue = targetX ?: 0f,
            animationSpec = BiliMotion.slideSpring(),
            label = "tabIndicator"
        )
        Box(modifier = Modifier.align(Alignment.Center)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeedTab("直播", selected == HomeTab.LIVE, { onSelect(HomeTab.LIVE) }, immersive) {
                    centers[HomeTab.LIVE] = it
                }
                FeedTab("商城", selected == HomeTab.MALL, { onSelect(HomeTab.MALL) }, immersive) {
                    centers[HomeTab.MALL] = it
                }
                FeedTab("关注", selected == HomeTab.FOLLOW, { onSelect(HomeTab.FOLLOW) }, immersive) {
                    centers[HomeTab.FOLLOW] = it
                }
                FeedTab("推荐", selected == HomeTab.RECOMMEND, { onSelect(HomeTab.RECOMMEND) }, immersive) {
                    centers[HomeTab.RECOMMEND] = it
                }
            }
            if (targetX != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset { IntOffset((indicatorX - 9.dp.toPx()).roundToInt(), 0) }
                        .width(20.dp)
                        .height(3.dp)
                        .background(if (immersive) Color.White else BiliAccent, CircleShape)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(if (immersive) Color.White.copy(alpha = 0.10f) else appField())
                .pressClickable(pressedScale = 0.94f) { onSearch() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = if (immersive) Color.White else appText(),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun FeedTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    immersive: Boolean,
    onCenter: (Float) -> Unit = {}
) {
    val color by animateColorAsState(
        targetValue = when {
            selected && immersive -> Color.White
            selected -> appText()
            immersive -> Color.White.copy(alpha = 0.58f)
            else -> appTextMuted()
        },
        animationSpec = tween(180),
        label = "tabColor"
    )
    val fontSize by animateFloatAsState(
        targetValue = if (selected) 17f else 15f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "tabFontSize"
    )
    Text(
        text = label,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .onGloballyPositioned { onCenter(it.positionInParent().x + it.size.width / 2f) }
            .pressClickable(pressedScale = 0.96f) { onClick() }
            .padding(bottom = 7.dp)
    )
}

@Composable
private fun FeedLoginPrompt() {
    Box(
        Modifier
            .fillMaxSize()
            .background(BiliBackground)
            .padding(top = 80.dp, bottom = 64.dp, start = 32.dp, end = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BiliAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = BiliAccent,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "看见你关注的新动态",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "登录后同步关注、点赞、收藏和弹幕",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))
            AppPrimaryButton(text = "登录并同步", onClick = { LoginPrompt.show() })
            Spacer(Modifier.height(12.dp))
            Text(
                text = "支持扫码和官方网页安全登录",
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 11.sp
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FeedPager(
    source: FeedSource,
    state: FeedState,
    viewModel: FeedViewModel,
    onComment: (VideoItem) -> Unit,
    onCoin: (VideoItem) -> Unit,
    onOpenOwner: (VideoItem) -> Unit,
    onToggleDanmaku: () -> Unit = {}
) {
    if (state.items.isEmpty()) {
        FeedStatus(state = state, onRetry = { viewModel.retry(source) })
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = rememberBiliPlayer()
    val authState by AuthManager.state.collectAsStateCompat()
    val qualityMode by LocalStore.qualityMode.collectAsStateCompat()
    val network by NetworkMonitor.profile.collectAsStateCompat()
    val danmakuEnabled by LocalStore.danmakuEnabled.collectAsStateCompat()
    val socialMap by viewModel.social.collectAsStateCompat()
    val playbackSpeed by LocalStore.playbackSpeed.collectAsStateCompat()
    val stretchFill by LocalStore.stretchFill.collectAsStateCompat()
    val loggedIn = authState is AuthState.LoggedIn
    val isVip = (authState as? AuthState.LoggedIn)?.user?.isVip == true
    val targetQn = effectiveQualityQn(qualityMode, isVip, network)

    val pagerState = rememberPagerState(
        initialPage = state.index.coerceIn(0, (state.items.size - 1).coerceAtLeast(0)),
        pageCount = { state.items.size }
    )
    val settledPage = pagerState.settledPage
    val activeItem = state.items.getOrNull(settledPage)

    var isReady by remember { mutableStateOf(false) }
    var playingBvid by remember { mutableStateOf("") }
    var readyBvid by remember { mutableStateOf("") }
    var playerError by remember { mutableStateOf<String?>(null) }
    var userPaused by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var forceProgressive by remember { mutableStateOf(false) }
    var currentInfo by remember { mutableStateOf<com.biliflow.app.data.PlayInfo?>(null) }
    var qualityNoticeBvid by remember { mutableStateOf("") }
    var danmakuItems by remember { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    val danmakuClock = remember { mutableLongStateOf(0L) }
    var recentDanmaku by remember { mutableStateOf<SentDanmaku?>(null) }
    var loadStartAt by remember { mutableLongStateOf(0L) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var boost by remember { mutableStateOf(false) }

    val qualityText = currentInfo?.quality?.let { qualityLabel(it) }
        ?: if (qualityMode == QualityMode.AUTO) "自动" else qualityMode.label

    val videoReady = isReady && readyBvid == activeItem?.bvid

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isReady = true
                    readyBvid = playingBvid
                    if (loadStartAt > 0L) {
                        android.util.Log.d(
                            "BiliFlow",
                            "first frame bvid=$playingBvid in ${android.os.SystemClock.elapsedRealtime() - loadStartAt}ms"
                        )
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                    player.play()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (currentInfo?.isDash == true && !forceProgressive) {
                    BiliApi.noteDashFailure(playingBvid)
                    forceProgressive = true
                    isReady = false
                    readyBvid = ""
                    UiMessages.show("高清流不可用，正在切换兼容清晰度")
                    retryToken++
                } else {
                    isReady = false
                    readyBvid = ""
                    playerError = error.localizedMessage ?: "播放失败"
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    PlayerLifecycle(player, userPaused)

    LaunchedEffect(playbackSpeed, boost) {
        player.setPlaybackSpeed(if (boost) 2f else playbackSpeed)
        android.util.Log.d("BiliFlow", "speed applied=$playbackSpeed")
    }

    LaunchedEffect(qualityMode, isVip) {
        if (qualityMode == QualityMode.Q1080 && !isVip) {
            UiMessages.show("1080P 需要大会员，当前自动使用最高可用清晰度")
        }
    }

    LaunchedEffect(settledPage) {
        danmakuClock.longValue = 0L
        forceProgressive = false
        currentInfo = null
        qualityNoticeBvid = ""
        danmakuItems = emptyList()
    }

    LaunchedEffect(settledPage, targetQn, retryToken, forceProgressive) {
        val item = state.items.getOrNull(settledPage) ?: return@LaunchedEffect
        loadStartAt = android.os.SystemClock.elapsedRealtime()
        isReady = false
        playingBvid = item.bvid
        readyBvid = ""
        playerError = null
        currentInfo = null
        userPaused = false
        player.pause()

        when (val result = viewModel.playback(item, targetQn, forceProgressive)) {
            is PlayResult.Error -> playerError = result.message
            is PlayResult.Success -> {
                currentInfo = result.info
                if (qualityMode != QualityMode.AUTO &&
                    targetQn > result.info.quality &&
                    qualityNoticeBvid != item.bvid
                ) {
                    qualityNoticeBvid = item.bvid
                    UiMessages.show("当前账号/视频最高可用 ${qualityLabel(result.info.quality)}")
                }
                runCatching {
                    player.repeatMode = when {
                        result.info.isDash -> Player.REPEAT_MODE_ONE
                        result.info.urls.size > 1 -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_ONE
                    }
                    player.setMediaSource(biliMediaSource(result.info))
                    player.prepare()
                    player.play()
                }.onFailure {
                    playerError = it.message ?: "播放失败"
                }
            }
        }
        viewModel.detail(item)
        viewModel.ensureSocial(item)
    }

    LaunchedEffect(settledPage, authState) {
        state.items.getOrNull(settledPage)?.let { viewModel.ensureSocial(it) }
    }

    LaunchedEffect(activeItem?.bvid, activeItem?.cid) {
        val item = activeItem ?: return@LaunchedEffect
        danmakuItems = if (item.cid > 0L) {
            BiliApi.danmaku(item.cid, item.aid)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    LaunchedEffect(player, danmakuEnabled, videoReady, settledPage) {
        while (danmakuEnabled && videoReady) {
            withFrameNanos { }
            danmakuClock.longValue = player.currentPosition.coerceAtLeast(0L)
        }
    }

    LaunchedEffect(settledPage, state.items.size) {
        viewModel.setIndex(source, settledPage)
        if (state.items.isNotEmpty() && settledPage >= state.items.size - 3) {
            viewModel.loadMore(source)
        }
    }

    // 预取后两条（含 DASH 可用性探测），滑动时直接起播
    LaunchedEffect(settledPage, state.items.size, targetQn) {
        if (state.items.isNotEmpty()) {
            viewModel.prefetchAround(state.items, settledPage, targetQn, forceProgressive)
        }
    }

    fun sendDanmaku(text: String) {
        val item = activeItem ?: return
        if (text.isBlank()) return
        if (!AuthManager.ensureLogin()) return
        val progress = player.currentPosition.coerceAtLeast(0L)
        scope.launch {
            try {
                val target = if (item.cid > 0L) item else viewModel.detail(item)
                if (target == null || target.cid <= 0L) {
                    UiMessages.show("无法获取视频信息")
                    return@launch
                }
                val dmid = BiliApi.sendDanmaku(target.cid, target.bvid, text, progress)
                danmakuItems = danmakuItems + DanmakuItem(
                    timeMs = progress,
                    mode = 1,
                    fontSize = 25,
                    color = 0xFFFFFF,
                    text = text
                )
                if (dmid > 0L) {
                    recentDanmaku = SentDanmaku(target.cid, dmid)
                    UiMessages.show("弹幕已发送，2 分钟内可撤回")
                } else {
                    UiMessages.show("弹幕已发送")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is BiliApi.BiliException && error.code == -101) {
                    LoginPrompt.show()
                    UiMessages.show("登录状态已失效，请重新登录")
                } else {
                    UiMessages.show(error.message ?: "弹幕发送失败")
                }
            }
        }
    }

    LaunchedEffect(recentDanmaku) {
        if (recentDanmaku != null) {
            delay(120_000)
            recentDanmaku = null
        }
    }

    fun recallDanmaku() {
        val sent = recentDanmaku ?: return
        scope.launch {
            try {
                BiliApi.recallDanmaku(sent.cid, sent.dmid)
                recentDanmaku = null
                UiMessages.show("弹幕已撤回")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                UiMessages.show(error.message ?: "弹幕撤回失败")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val backdrop = activeItem?.cover.orEmpty()
        if (!stretchFill && backdrop.isNotBlank()) {
            RemoteImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                targetWidth = 720,
                placeholder = Color.Black
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.66f)))
        }
        BiliPlayerSurface(
            player = player,
            fillScreen = stretchFill,
            modifier = Modifier.fillMaxSize()
        )

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = state.items.getOrNull(page) ?: return@VerticalPager
            val settled = page == settledPage
            val scrolling = pagerState.isScrollInProgress
            val interactive = settled && !scrolling
            val pageReady = isReady && settled && readyBvid == item.bvid
            val showProgress = pageReady && durationMs > 0L
            val progress = if (showProgress) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

            FeedPage(
                item = item,
                social = socialMap[item.bvid] ?: SocialState(),
                showVideo = settled && !scrolling,
                playerReady = pageReady,
                playerError = if (settled && playingBvid == item.bvid) playerError else null,
                paused = pageReady && !player.playWhenReady,
                progress = progress,
                danmaku = danmakuItems,
                danmakuEnabled = danmakuEnabled,
                danmakuPosition = danmakuClock,
                loggedIn = loggedIn,
                interactive = interactive,
                onTogglePlay = {
                    val wasPlaying = player.playWhenReady
                    player.playWhenReady = !wasPlaying
                    userPaused = wasPlaying
                },
                onLike = { viewModel.toggleLike(item) },
                onDoubleTapLike = {
                    if (!(socialMap[item.bvid] ?: SocialState()).liked) {
                        viewModel.toggleLike(item)
                    }
                },
                onComment = { onComment(item) },
                onCoin = { onCoin(item) },
                onFavorite = { viewModel.toggleFavorite(item) },
                onShare = { shareVideo(context, item) },
                onFollow = { viewModel.toggleFollow(item) },
                onRetry = { retryToken++ },
                onOpenOwner = { onOpenOwner(item) },
                onRequireLogin = { LoginPrompt.show() },
                onSendDanmaku = { text -> sendDanmaku(text) },
                recallAvailable = recentDanmaku != null,
                onRecall = { recallDanmaku() },
                speed = playbackSpeed,
                qualityText = if (settled) qualityText else "",
                fillScreen = stretchFill,
                onOpenSpeed = { showSpeedSheet = true },
                onOpenQuality = { showQualitySheet = true },
                onToggleFill = {
                    LocalStore.setStretchFill(!stretchFill)
                    UiMessages.show(if (!stretchFill) "铺满模式：画面按屏幕裁切" else "完整模式：显示完整画面")
                },
                boosting = boost,
                onBoost = { boost = it },
                onToggleDanmaku = onToggleDanmaku,
                durationMs = durationMs,
                onSeek = { fraction ->
                    if (durationMs > 0L) player.seekTo((fraction * durationMs).toLong())
                }
            )
        }
    }

    if (showSpeedSheet) {
        PlayerSelectSheet(
            title = "播放倍速",
            options = PLAYER_SPEED_OPTIONS,
            selected = speedLabelOf(playbackSpeed),
            onSelect = { label ->
                val value = speedFromLabel(label)
                LocalStore.setPlaybackSpeed(value)
                player.setPlaybackSpeed(value)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    if (showQualitySheet) {
        PlayerSelectSheet(
            title = "清晰度",
            options = PLAYER_QUALITY_OPTIONS,
            selected = qualityMode.label,
            onSelect = { label ->
                LocalStore.setQualityMode(qualityModeOf(label))
                showQualitySheet = false
            },
            onDismiss = { showQualitySheet = false }
        )
    }
}

private data class SentDanmaku(val cid: Long, val dmid: Long)

@Composable
private fun FeedStatus(state: FeedState, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BiliBackground)
            .padding(top = 80.dp, bottom = 64.dp, start = 32.dp, end = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.loading) {
                LoadingDots()
                Spacer(Modifier.height(18.dp))
                Text("正在为你挑选视频", color = Color.White.copy(alpha = 0.64f), fontSize = 13.sp)
            } else {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(BiliAccent.copy(alpha = 0.13f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = BiliAccent,
                        modifier = Modifier.size(27.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = if (state.error != null) "视频暂时没有加载出来" else "这里还没有内容",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.error ?: "切换其他频道，或稍后再来看看",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(20.dp))
                AppPrimaryButton(text = "重新加载", onClick = onRetry)
            }
        }
    }
}




