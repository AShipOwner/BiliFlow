package com.biliflow.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** 全局动效曲线：按压要快、回弹要弹、转场要稳。 */
object BiliMotion {
    fun <T> pressSpring() = spring<T>(dampingRatio = 0.55f, stiffness = 700f)
    fun <T> popSpring() = spring<T>(dampingRatio = 0.34f, stiffness = 520f)
    fun <T> slideSpring() = spring<T>(dampingRatio = 0.82f, stiffness = 480f)
    val TransitionEnter = tween<Float>(240, easing = FastOutSlowInEasing)
    val TransitionExit = tween<Float>(140, easing = FastOutSlowInEasing)
}

/** 无涟漪点击 + 按压回弹（替代 noRippleClickable 用于需要手感的位置）。 */
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.9f,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = BiliMotion.pressSpring(),
        label = "pressScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/** 状态翻转为 true 时做一次「缩-弹」脉冲（点赞/收藏/关注），挂载时不触发。 */
@Composable
fun rememberPopScale(trigger: Boolean, initialScale: Float = 0.7f): Float {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger && !previous) {
            scale.snapTo(initialScale)
            scale.animateTo(1f, BiliMotion.popSpring())
        }
        previous = trigger
    }
    return scale.value
}

/** 全屏页面进场：右滑入 + 淡入（播放页可改为缩放进场）。只播放一次，退出由系统返回处理。 */
@Composable
fun Modifier.screenEnter(
    slideFromRight: Boolean = true,
    scaleFrom: Float = 1f
): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, BiliMotion.TransitionEnter)
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationX = if (slideFromRight) size.width * (1f - p) * 0.22f else 0f
        val scale = scaleFrom + (1f - scaleFrom) * p
        scaleX = scale
        scaleY = scale
    }
}

/** 数字/短文本滚动切换（点赞数、倍速标签等）。 */
@Composable
fun AnimatedSwapText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(160)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 2 }) togetherWith
                (fadeOut(tween(110)) + slideOutVertically(tween(150)) { -it / 2 })
        },
        label = "swapText"
    ) { target ->
        Text(text = target, style = style, modifier = modifier)
    }
}

/** 骨架屏微光扫过。progress 在 draw 阶段读取，不触发重组。 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    val dark = LocalAppDark.current
    val base = if (dark) Color(0xFF232329) else Color(0xFFE9E9EE)
    val highlight = if (dark) Color(0xFF35353D) else Color(0xFFF8F8FB)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerSweep"
    )
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val sweep = size.width * 1.5f
                onDrawBehind {
                    val x = -sweep + progress * (size.width + sweep * 2f)
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(base, highlight, base),
                            start = Offset(x, 0f),
                            end = Offset(x + sweep, size.height)
                        )
                    )
                }
            }
    )
}

/** 品牌色三点加载（替代全屏转圈，用于 Feed 首屏）。 */
@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    color: Color = BiliAccent
) {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(960, delayMillis = index * 160, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot$index"
            )
            val pulse = kotlin.math.sin(phase * Math.PI).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * pulse
                        val s = 0.75f + 0.45f * pulse
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}