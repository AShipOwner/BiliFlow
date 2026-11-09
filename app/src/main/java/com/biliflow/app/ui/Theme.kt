package com.biliflow.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.biliflow.app.data.LocalStore

// 色值来自抖音原包资源表（DouyinTokens.kt），浅色/深色两套共用同一个品牌色。
val BiliAccent = DouyinColor.Accent
val BiliCyan = Color(0xFF25F4EE)
val BiliYellow = DouyinColor.Yellow
val BiliBackground = DouyinColor.Black
val BiliSurface = DouyinColor.Ink
val BiliSurfaceRaised = DouyinColor.SurfaceRaised
val BiliMuted = Color(0xFFB0B0B3)
val BiliDivider = Color(0xFF2A2A2A)
val BiliHairline = DouyinColor.White08
val BiliTextSecondary = DouyinColor.White60

val LocalAppDark = staticCompositionLocalOf { true }

/** 页面底色（跟随主题） */
@Composable
fun appBackground(): Color = if (LocalAppDark.current) Color(0xFF0B0B0C) else Color(0xFFF7F7F8)

/** 卡片底色 */
@Composable
fun appSurface(): Color = if (LocalAppDark.current) Color(0xFF161823) else Color.White

/** 主文字 */
@Composable
fun appText(): Color = if (LocalAppDark.current) Color.White else Color(0xFF161823)

/** 次要文字 */
@Composable
fun appTextMuted(): Color = if (LocalAppDark.current) Color(0xFFB0B0B3) else Color(0xFF8A8A8F)

/** 分隔线 */
@Composable
fun appDivider(): Color = if (LocalAppDark.current) Color(0xFF2A2A2A) else Color(0xFFEDEDEF)

/** 输入框 / 次级容器 */
@Composable
fun appField(): Color = if (LocalAppDark.current) Color(0xFF1F1F23) else Color(0xFFF1F1F3)

/** chip / 轻按钮 */
@Composable
fun appChip(): Color = if (LocalAppDark.current) Color(0xFF262626) else Color(0xFFEDEDEF)

private val DarkScheme = darkColorScheme(
    primary = BiliAccent,
    onPrimary = Color.White,
    secondary = BiliCyan,
    onSecondary = Color.Black,
    background = Color(0xFF0B0B0C),
    onBackground = Color.White,
    surface = DouyinColor.Ink,
    onSurface = Color.White,
    surfaceVariant = DouyinColor.SurfaceRaised,
    onSurfaceVariant = BiliMuted,
    outline = Color(0xFF2A2A2A)
)

private val LightScheme = lightColorScheme(
    primary = BiliAccent,
    onPrimary = Color.White,
    secondary = BiliCyan,
    onSecondary = Color.Black,
    background = Color(0xFFF7F7F8),
    onBackground = Color(0xFF161823),
    surface = Color.White,
    onSurface = Color(0xFF161823),
    surfaceVariant = Color(0xFFF1F1F2),
    onSurfaceVariant = Color(0xFF8A8A8F),
    outline = Color(0xFFEDEDEF)
)

private val BiliTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = DouyinType.display),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = DouyinType.headline),
        bodyLarge = bodyLarge.copy(lineHeight = 22.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 20.sp, fontSize = DouyinType.title)
    )
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun BiliFlowTheme(content: @Composable () -> Unit) {    val mode by LocalStore.themeMode.collectAsStateCompat()
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalAppDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = BiliTypography,
            content = content
        )
    }
}
