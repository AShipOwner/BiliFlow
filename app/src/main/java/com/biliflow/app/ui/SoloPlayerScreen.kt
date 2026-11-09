package com.biliflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.biliflow.app.data.AuthManager
import com.biliflow.app.data.AuthState
import com.biliflow.app.data.BiliApi
import com.biliflow.app.data.DanmakuItem
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

@Composable
fun SoloPlayerScreen(
    seed: VideoItem,
    viewModel: FeedViewModel,
    onOpenSpace: (Long) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = rememberBiliPlayer()
    val authState by AuthManager.state.collectAsStateCompat()
    val qualityMode by LocalStore.qualityMode.collectAsStateCompat()
    val network by NetworkMonitor.profile.collectAsStateCompat()
    val danmakuEnabled by LocalStore.danmakuEnabled.collectAsStateCompat()
    val playbackSpeed by LocalStore.playbackSpeed.collectAsStateCompat()
    val stretchFill by LocalStore.stretchFill.collectAsStateCompat()
    val socialMap by viewModel.social.collectAsStateCompat()
    val loggedIn = authState is AuthState.LoggedIn
    val isVip = (authState as? AuthState.LoggedIn)?.user?.isVip == true
    val targetQn = effectiveQualityQn(qualityMode, isVip, network)

    var item by remember(seed.bvid) { mutableStateOf(seed) }
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
    var showComments by remember { mutableStateOf(false) }
    var showCoin by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    val qualityText = currentInfo?.quality?.let { qualityLabel(it) }
        ?: if (qualityMode == QualityMode.AUTO) "自动" else qualityMode.label

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isReady = true
                    readyBvid = playingBvid
                }
                if (playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                    player.play()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (currentInfo?.isDash == true && !forceProgressive) {
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

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(seed.bvid, targetQn, retryToken, forceProgressive) {
        isReady = false
        playingBvid = seed.bvid
        readyBvid = ""
        playerError = null
        currentInfo = null
        userPaused = false
        player.pause()

        val detailed = viewModel.detail(seed)
        if (detailed != null) item = detailed
        val target = detailed ?: seed
        if (target.cid <= 0L) {
            playerError = "无法获取视频信息"
            return@LaunchedEffect
        }

        when (val result = viewModel.playback(target, targetQn, forceProgressive)) {
            is PlayResult.Error -> playerError = result.message
            is PlayResult.Success -> {
                currentInfo = result.info
                if (qualityMode != QualityMode.AUTO &&
                    targetQn > result.info.quality &&
                    qualityNoticeBvid != target.bvid
                ) {
                    qualityNoticeBvid = target.bvid
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
        viewModel.ensureSocial(target)
        danmakuItems = if (target.cid > 0L) BiliApi.danmaku(target.cid, target.aid) else emptyList()
    }

    LaunchedEffect(item.bvid, item.cid) {
        if (item.cid > 0L) {
            danmakuItems = BiliApi.danmaku(item.cid, item.aid)
        }
    }

    LaunchedEffect(authState) {
        viewModel.ensureSocial(item)
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    LaunchedEffect(player, danmakuEnabled, isReady, readyBvid) {
        while (danmakuEnabled && isReady && readyBvid == item.bvid) {
            withFrameNanos { }
            danmakuClock.longValue = player.currentPosition.coerceAtLeast(0L)
        }
    }

    fun sendDanmaku(text: String) {
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
                BiliApi.sendDanmaku(target.cid, target.bvid, text, progress)
                danmakuItems = danmakuItems + DanmakuItem(progress, 1, 25, 0xFFFFFF, text)
                UiMessages.show("弹幕已发送")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error is BiliApi.BiliException && error.code == -101) {
                    LoginPrompt.show()
                } else {
                    UiMessages.show(error.message ?: "弹幕发送失败")
                }
            }
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black).screenEnter(slideFromRight = false, scaleFrom = 0.96f)) {
        if (!stretchFill && item.cover.isNotBlank()) {
            RemoteImage(
                url = item.cover,
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

        val videoReady = isReady && readyBvid == item.bvid
        val social = socialMap[item.bvid] ?: SocialState()
        FeedPage(
            item = item,
            social = social,
            showVideo = true,
            playerReady = videoReady,
            playerError = playerError,
            paused = videoReady && !player.playWhenReady,
            progress = if (videoReady && durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
            danmaku = danmakuItems,
            danmakuEnabled = danmakuEnabled,
            danmakuPosition = danmakuClock,
            loggedIn = loggedIn,
            interactive = true,
            onTogglePlay = {
                val wasPlaying = player.playWhenReady
                player.playWhenReady = !wasPlaying
                userPaused = wasPlaying
            },
            onLike = { viewModel.toggleLike(item) },
            onDoubleTapLike = {
                if (!social.liked) viewModel.toggleLike(item)
            },
            onComment = { showComments = true },
            onCoin = { if (loggedIn) showCoin = true else LoginPrompt.show() },
            onFavorite = { viewModel.toggleFavorite(item) },
            onShare = { shareVideo(context, item) },
            onFollow = { viewModel.toggleFollow(item) },
            onRetry = { retryToken++ },
            onOpenOwner = { onOpenSpace(item.ownerMid) },
            onRequireLogin = { LoginPrompt.show() },
            onSendDanmaku = { text -> sendDanmaku(text) },
            onToggleDanmaku = {
                val next = !danmakuEnabled
                LocalStore.setDanmakuEnabled(next)
                UiMessages.show(if (next) "弹幕已开启" else "弹幕已关闭")
            },
            speed = playbackSpeed,
            qualityText = qualityText,
            fillScreen = stretchFill,
            onOpenSpeed = { showSpeedSheet = true },
            onOpenQuality = { showQualitySheet = true },
            onToggleFill = {
                LocalStore.setStretchFill(!stretchFill)
                UiMessages.show(if (!stretchFill) "铺满模式：画面按屏幕裁切" else "完整模式：显示完整画面")
            },
            durationMs = durationMs,
            onSeek = { fraction ->
                if (durationMs > 0L) player.seekTo((fraction * durationMs).toLong())
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.66f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .pressClickable(pressedScale = 0.85f) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .pressClickable(pressedScale = 0.85f) { openBiliVideo(context, item) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "在 B 站打开",
                    tint = Color.White
                )
            }
        }
    }

    if (showComments) {
        CommentsSheet(
            item = item,
            viewModel = viewModel,
            onDismiss = { showComments = false },
            onOpenSpace = { mid ->
                showComments = false
                onOpenSpace(mid)
            }
        )
    }

    if (showCoin) {
        CoinSheet(
            item = item,
            user = (authState as? AuthState.LoggedIn)?.user,
            social = socialMap[item.bvid] ?: SocialState(),
            onDismiss = { showCoin = false },
            onConfirm = { count, alsoLike ->
                viewModel.addCoin(item, count, alsoLike)
                showCoin = false
            }
        )
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

