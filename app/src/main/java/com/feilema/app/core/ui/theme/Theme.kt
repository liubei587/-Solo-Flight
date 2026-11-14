package com.feilema.app.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = White,
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = BrandBlueOnContainer,
    secondary = BrandIndigo,
    onSecondary = White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = MintAccent,
    onTertiary = Color(0xFF042F2E),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = Color(0xFFDC2626),
    onError = White,
)

internal val DarkColors = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF0B1220),
    primaryContainer = BrandBlueContainerDark,
    onPrimaryContainer = BrandBlueOnContainerDark,
    secondary = BrandIndigoDark,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = MintAccentDark,
    onTertiary = Color(0xFF042F2E),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = RoseAccentDark,
    onError = Color(0xFF450A0A),
)

/**
 * 应用主题。
 *
 * @param seedColor 用户选择的主题色；null（或默认海蓝）时继续用手工调过的默认配色，
 *                  其他颜色由 [buildColorScheme] 生成整套强调色。
 */
@Composable
fun FeiLeMaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Long? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        seedColor != null -> buildColorScheme(seedColor, darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FeiLeMaTypography,
        content = content,
    )
}
