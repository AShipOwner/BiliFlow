package com.biliflow.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.biliflow.app.data.Http
import com.biliflow.app.data.PlayInfo

private const val MEDIA_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun rememberBiliPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    // 起播缓冲压到 0.8s，滑动切下一个视频时明显更快
                    .setBufferDurationsMs(20_000, 60_000, 800, 1_500)
                    .build()
            )
            .build()
            .apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun biliMediaSource(info: PlayInfo): MediaSource {
    val headers = mutableMapOf(
        "Referer" to "https://www.bilibili.com/",
        "User-Agent" to MEDIA_UA,
        "Origin" to "https://www.bilibili.com"
    )
    if (Http.cookie.isNotBlank()) {
        headers["Cookie"] = Http.cookie
    }
    val factory = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(headers)
        .setAllowCrossProtocolRedirects(true)

    if (info.isDash) {
        val video = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(info.dashVideoUrl.orEmpty()))
        val audio = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(info.dashAudioUrl.orEmpty()))
        return MergingMediaSource(video, audio)
    }

    val sources = info.urls.map { url ->
        ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(url))
    }
    require(sources.isNotEmpty()) { "播放地址为空" }
    return if (sources.size == 1) {
        sources[0]
    } else {
        ConcatenatingMediaSource(*sources.toTypedArray())
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BiliPlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    fillScreen: Boolean = false
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setResizeMode(if (fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                keepScreenOn = true
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.setResizeMode(
                if (fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else AspectRatioFrameLayout.RESIZE_MODE_FIT
            )
            view.requestLayout()
        },
        modifier = modifier
    )
}

@Composable
fun PlayerLifecycle(player: ExoPlayer, userPaused: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val paused by rememberUpdatedState(userPaused)
    DisposableEffect(lifecycleOwner, player) {
        var resumeOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeOnStart = player.isPlaying
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    if (resumeOnStart && !paused) player.play()
                    resumeOnStart = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
