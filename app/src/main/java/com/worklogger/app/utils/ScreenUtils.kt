package com.worklogger.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 屏幕适配工具
 * 解决低端机（低分辨率）显示不全、高端机（高分辨率）显示过小的问题
 * 
 * 以360dp为基准屏幕宽度，按比例缩放所有UI元素
 * - 低端机（<360dp）：整体缩小，保证内容完整显示
 * - 高端机（>360dp）：整体放大，提升可读性
 * - 普通机（~360dp）：保持默认
 */

// 屏幕尺寸类别
enum class ScreenSize(val widthDp: Int, val label: String) {
    SMALL(0, "小屏"),       // <320dp  百元机、小屏低端机
    MEDIUM(320, "中屏"),    // 320-400dp 大部分手机
    LARGE(400, "大屏"),     // 400-600dp 大屏手机
    XLARGE(600, "超大屏")   // >600dp 平板
}

// 自适应排版数据
data class ResponsiveValues(
    val scaleFactor: Float,
    val bodyFontScale: Float,
    val titleFontScale: Float,
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp,
    val iconSize: Dp,
    val cardPadding: Dp,
    val spacingSmall: Dp,
    val spacingMedium: Dp,
    val spacingLarge: Dp,
    val minTouchTarget: Dp
)

// CompositionLocal 提供全局访问
val LocalResponsiveValues = compositionLocalOf { defaultResponsiveValues() }

// 默认值（基准360dp）
fun defaultResponsiveValues() = ResponsiveValues(
    scaleFactor = 1.0f,
    bodyFontScale = 1.0f,
    titleFontScale = 1.0f,
    paddingSmall = 4.dp,
    paddingMedium = 8.dp,
    paddingLarge = 16.dp,
    iconSize = 24.dp,
    cardPadding = 12.dp,
    spacingSmall = 4.dp,
    spacingMedium = 8.dp,
    spacingLarge = 16.dp,
    minTouchTarget = 48.dp
)

@Composable
fun rememberResponsiveValues(): ResponsiveValues {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp
    
    // 以360dp为基准计算缩放因子
    val rawScale = screenWidthDp / 360f
    // 限制缩放范围：最小0.82（避免低端机字太小），最大1.15（避免大屏字太大）
    val scaleFactor = rawScale.coerceIn(0.82f, 1.15f)
    
    return when {
        // 小屏低端机：明显缩小，保证内容完整
        screenWidthDp < 320 -> ResponsiveValues(
            scaleFactor = 0.82f,
            bodyFontScale = 0.88f,
            titleFontScale = 0.90f,
            paddingSmall = 3.dp,
            paddingMedium = 6.dp,
            paddingLarge = 12.dp,
            iconSize = 20.dp,
            cardPadding = 8.dp,
            spacingSmall = 3.dp,
            spacingMedium = 6.dp,
            spacingLarge = 12.dp,
            minTouchTarget = 44.dp
        )
        // 中屏普通机：轻微缩小
        screenWidthDp < 360 -> ResponsiveValues(
            scaleFactor = 0.92f,
            bodyFontScale = 0.95f,
            titleFontScale = 0.95f,
            paddingSmall = 4.dp,
            paddingMedium = 7.dp,
            paddingLarge = 14.dp,
            iconSize = 22.dp,
            cardPadding = 10.dp,
            spacingSmall = 4.dp,
            spacingMedium = 7.dp,
            spacingLarge = 14.dp,
            minTouchTarget = 48.dp
        )
        // 大屏手机：轻微放大
        screenWidthDp >= 400 && screenWidthDp < 600 -> ResponsiveValues(
            scaleFactor = 1.05f,
            bodyFontScale = 1.05f,
            titleFontScale = 1.05f,
            paddingSmall = 5.dp,
            paddingMedium = 10.dp,
            paddingLarge = 18.dp,
            iconSize = 26.dp,
            cardPadding = 14.dp,
            spacingSmall = 5.dp,
            spacingMedium = 10.dp,
            spacingLarge = 18.dp,
            minTouchTarget = 48.dp
        )
        // 超大屏/平板：明显放大
        screenWidthDp >= 600 -> ResponsiveValues(
            scaleFactor = 1.15f,
            bodyFontScale = 1.10f,
            titleFontScale = 1.12f,
            paddingSmall = 6.dp,
            paddingMedium = 12.dp,
            paddingLarge = 24.dp,
            iconSize = 28.dp,
            cardPadding = 16.dp,
            spacingSmall = 6.dp,
            spacingMedium = 12.dp,
            spacingLarge = 24.dp,
            minTouchTarget = 52.dp
        )
        // 标准屏
        else -> defaultResponsiveValues()
    }
}

// 扩展函数：按比例缩放TextUnit（用于sp字体大小）
@Composable
fun TextUnit.responsive(): TextUnit {
    val rv = LocalResponsiveValues.current
    return (this.value * rv.bodyFontScale).sp
}

@Composable
fun TextUnit.responsiveTitle(): TextUnit {
    val rv = LocalResponsiveValues.current
    return (this.value * rv.titleFontScale).sp
}

// 获取当前屏幕尺寸类别
@Composable
fun getScreenSize(): ScreenSize {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 320 -> ScreenSize.SMALL
        widthDp < 400 -> ScreenSize.MEDIUM
        widthDp < 600 -> ScreenSize.LARGE
        else -> ScreenSize.XLARGE
    }
}

// 屏幕宽度dp
@Composable
fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

// 屏幕高度dp
@Composable
fun screenHeightDp(): Int = LocalConfiguration.current.screenHeightDp

// 屏幕密度
@Composable
fun screenDensity(): Float = LocalConfiguration.current.densityDpi / 160f
