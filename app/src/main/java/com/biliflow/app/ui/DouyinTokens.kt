package com.biliflow.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biliflow.app.R

/**
 * 真机抖音包（com.ss.android.ugc.aweme 41.0.0）资源表与 Lynx 模板里实测出来的设计令牌。
 *
 * 来源：
 *  - 颜色/尺寸：`aapt2 dump resources`（resources.arsc），按使用次数排序提取；
 *  - 字阶/圆角：`assets/offline/**/main.json`（Lynx 模板，693 个）中 style.font_size / radius 统计；
 *  - 字体：包内 `res/font/douyin_number_*.otf`（抖音自带的数字字体，用于点赞/评论/播放数）。
 *
 * 重新生成：`BiliFlow/tools/extract_douyin_tokens.py`
 */
object DouyinColor {
    /** 抖音主文本色，也是大量半透明遮罩的基色（#161823，621 次引用） */
    val Ink = Color(0xFF161823)
    /** 主色（品牌红，#FE2C55，303 次） */
    val Accent = Color(0xFFFE2C55)
    /** 次红（#FE3824） */
    val AccentAlt = Color(0xFFFE3824)
    /** 高亮黄（#FACE15，128 次） */
    val Yellow = Color(0xFFFACE15)
    /** 深色面（#161616，77 次） */
    val Surface = Color(0xFF161616)
    /** 抬升面（#262626，43 次） */
    val SurfaceRaised = Color(0xFF262626)
    /** 次级面/描边（#393B44） */
    val SurfaceSoft = Color(0xFF393B44)
    /** 链接蓝（#168EF9） */
    val Blue = Color(0xFF168EF9)
    /** 深蓝（#04498D，62 次） */
    val BlueDeep = Color(0xFF04498D)
    /** 浅色底（#F3F3F4，82 次） */
    val LightBg = Color(0xFFF3F3F4)
    /** 浅色底变体（#F8F8F8） */
    val LightBgAlt = Color(0xFFF8F8F8)
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)

    val Ink12 = Color(0x1F161823)
    val Ink20 = Color(0x33161823)
    val Ink32 = Color(0x52161823)
    val Ink60 = Color(0x99161823)
    val Ink75 = Color(0xBF161823)
    val White80 = Color(0xCCFFFFFF)
    val White60 = Color(0x99FFFFFF)
    val White32 = Color(0x52FFFFFF)
    val White15 = Color(0x26FFFFFF)
    val White08 = Color(0x14FFFFFF)
}

/** 间距：资源表里出现频率最高的 dp 值（16/8/12/4/20/2/44/24/32/48…） */
object DouyinSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 6.dp
    val sm = 8.dp
    val m = 10.dp
    val ml = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 44.dp
    /** 底栏高度（布局实测 40–52dp，取 48dp） */
    val tabBar = 48.dp
}

/** 圆角：Lynx 模板 radius 统计（8 / 12 / 10 / 2 / 4 / 6 / 100） */
object DouyinRadius {
    val xs = 2.dp
    val s = 4.dp
    val m = 6.dp
    val l = 8.dp
    val xl = 10.dp
    val xxl = 12.dp
    val huge = 16.dp
    val pill = 100.dp
}

/** 字阶：Lynx 模板 font_size 统计（12/14/13/10/11/16/15/17） */
object DouyinType {
    val micro = 9.sp
    val caption = 10.sp
    val bodySm = 11.sp
    val body = 12.sp
    val bodyStrong = 13.sp
    val title = 14.sp
    val titleLg = 15.sp
    val headline = 16.sp
    val headlineLg = 17.sp
    val display = 20.sp
}

/** 抖音数字字体：点赞/评论/播放数用它，观感与原生包一致 */
val DouyinNumber = FontFamily(
    Font(R.font.douyin_number_medium, FontWeight.Medium),
    Font(R.font.douyin_number_bold, FontWeight.Bold),
    Font(R.font.douyin_number_extra_bold, FontWeight.ExtraBold)
)

/** 窄体数字（用于大号计数、播放量） */
val DouyinNumberCondensed = FontFamily(
    Font(R.font.douyin_number_condensed_medium, FontWeight.Medium),
    Font(R.font.douyin_number_extra_condensed_medium, FontWeight.Bold)
)
