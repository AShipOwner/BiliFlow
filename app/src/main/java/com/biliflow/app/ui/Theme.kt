package com.biliflow.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.biliflow.app.data.LocalStore

val BiliAccent = Color(0xFFE95783)
val BiliCyan = Color(0xFF4EBFB3)
val BiliYellow = Color(0xFFF2BB4F)
val BiliBackground = Color(0xFF0C0D10)
val BiliSurface = Color(0xFF17191F)
val BiliSurfaceRaised = Color(0xFF22242B)
val BiliMuted = Color(0xFF9A9EAA)
val BiliDivider = Color(0xFF292B32)
val BiliHairline = Color(0x14FFFFFF)
val BiliTextSecondary = Color(0xFF9A9EAA)

val LocalAppDark = staticCompositionLocalOf { true }

@Composable
fun appBackground(): Color = if (LocalAppDark.current) Color(0xFF0F1014) else Color(0xFFF6F7F9)

@Composable
fun appSurface(): Color = if (LocalAppDark.current) Color(0xFF17191F) else Color.White

@Composable
fun appSurfaceRaised(): Color = if (LocalAppDark.current) Color(0xFF202229) else Color.White

@Composable
fun appText(): Color = if (LocalAppDark.current) Color(0xFFF4F5F7) else Color(0xFF191B20)

@Composable
fun appTextMuted(): Color = if (LocalAppDark.current) Color(0xFF9A9EAA) else Color(0xFF747984)

@Composable
fun appDivider(): Color = if (LocalAppDark.current) Color(0xFF2A2C33) else Color(0xFFE9EBEF)

@Composable
fun appField(): Color = if (LocalAppDark.current) Color(0xFF1D1F26) else Color(0xFFF0F1F4)

@Composable
fun appChip(): Color = if (LocalAppDark.current) Color(0xFF262931) else Color(0xFFEDEFF3)

@Composable
fun appAccentSoft(): Color = BiliAccent.copy(alpha = if (LocalAppDark.current) 0.17f else 0.10f)

private val DarkScheme = darkColorScheme(
    primary = BiliAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF42232F),
    onPrimaryContainer = Color(0xFFFFDDE6),
    secondary = BiliCyan,
    onSecondary = Color(0xFF082724),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF17191F),
    onSurface = Color(0xFFF4F5F7),
    surfaceVariant = Color(0xFF22242B),
    onSurfaceVariant = Color(0xFF9A9EAA),
    outline = Color(0xFF2A2C33),
    error = Color(0xFFFFB4AB)
)

private val LightScheme = lightColorScheme(
    primary = BiliAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4EB),
    onPrimaryContainer = Color(0xFF56182D),
    secondary = BiliCyan,
    onSecondary = Color.White,
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF191B20),
    surface = Color.White,
    onSurface = Color(0xFF191B20),
    surfaceVariant = Color(0xFFF0F1F4),
    onSurfaceVariant = Color(0xFF747984),
    outline = Color(0xFFE2E4E9),
    error = Color(0xFFBA1A1A)
)

private val BiliTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    )
}

private val BiliShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun BiliFlowTheme(content: @Composable () -> Unit) {
    val mode by LocalStore.themeMode.collectAsStateCompat()
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
            shapes = BiliShapes,
            content = content
        )
    }
}
