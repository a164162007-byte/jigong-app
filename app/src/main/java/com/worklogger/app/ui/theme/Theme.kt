package com.worklogger.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
