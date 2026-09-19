package com.worklogger.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.worklogger.app.utils.LocalResponsiveValues
import com.worklogger.app.utils.rememberResponsiveValues

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = SurfaceLight,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = OnSurfaceLight,
    secondary = Secondary,
    onSecondary = SurfaceLight,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SurfaceLight,
    tertiary = Overtime,
    onTertiary = SurfaceLight,
    tertiaryContainer = OvertimeLight,
    onTertiaryContainer = OnSurfaceLight,
    error = Error,
    onError = SurfaceLight,
    errorContainer = Error.copy(alpha = 0.1f),
    onErrorContainer = Error,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = BackgroundLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OnSurfaceVariantLight,
    outlineVariant = OnSurfaceVariantLight.copy(alpha = 0.5f)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = SurfaceDark,
    secondary = Secondary,
    onSecondary = BackgroundDark,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SurfaceDark,
    tertiary = OvertimeLight,
    onTertiary = BackgroundDark,
    tertiaryContainer = Overtime,
    onTertiaryContainer = SurfaceDark,
    error = Error,
    onError = BackgroundDark,
    errorContainer = Error.copy(alpha = 0.2f),
    onErrorContainer = Error,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = BackgroundDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OnSurfaceVariantDark,
    outlineVariant = OnSurfaceVariantDark.copy(alpha = 0.5f)
)

@Composable
fun adaptiveTypography(bodyScale: Float = 1f, titleScale: Float = 1f): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (57 * bodyScale).sp,
            lineHeight = (64 * bodyScale).sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (45 * bodyScale).sp,
            lineHeight = (52 * bodyScale).sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (36 * bodyScale).sp,
            lineHeight = (44 * bodyScale).sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (32 * titleScale).sp,
            lineHeight = (40 * titleScale).sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (28 * titleScale).sp,
            lineHeight = (36 * titleScale).sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (24 * titleScale).sp,
            lineHeight = (32 * titleScale).sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (22 * titleScale).sp,
            lineHeight = (28 * titleScale).sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16 * titleScale).sp,
            lineHeight = (24 * titleScale).sp,
            letterSpacing = (0.15 * titleScale).sp
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * titleScale).sp,
            lineHeight = (20 * titleScale).sp,
            letterSpacing = (0.1 * titleScale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (16 * bodyScale).sp,
            lineHeight = (24 * bodyScale).sp,
            letterSpacing = (0.5 * bodyScale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (14 * bodyScale).sp,
            lineHeight = (20 * bodyScale).sp,
            letterSpacing = (0.25 * bodyScale).sp
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = (12 * bodyScale).sp,
            lineHeight = (16 * bodyScale).sp,
            letterSpacing = (0.4 * bodyScale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (14 * bodyScale).sp,
            lineHeight = (20 * bodyScale).sp,
            letterSpacing = (0.1 * bodyScale).sp
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (12 * bodyScale).sp,
            lineHeight = (16 * bodyScale).sp,
            letterSpacing = (0.5 * bodyScale).sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (11 * bodyScale).sp,
            lineHeight = (16 * bodyScale).sp,
            letterSpacing = (0.5 * bodyScale).sp
        )
    )
}

@Composable
fun WorkLoggerTheme(
    theme: String = "system",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    // 自适应排版：根据屏幕尺寸计算缩放因子
    val responsiveValues = rememberResponsiveValues()
    val adaptiveTypography = adaptiveTypography(
        bodyScale = responsiveValues.bodyFontScale,
        titleScale = responsiveValues.titleFontScale
    )
    
    CompositionLocalProvider(LocalResponsiveValues provides responsiveValues) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = adaptiveTypography,
            content = content
        )
    }
}
