package com.feilema.app.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/** 深墨色，用于浅色底上的文字。 */
private val Ink = Color(0xFF0B1220)

/**
 * 用一枚种子色生成整套 Material3 配色。
 *
 * 设计约束：
 * - 背景、表面、正文继续沿用中性色，保证界面稳定性；
 * - 强调色（primary / secondary / tertiary）在浅色主题统一压暗、深色主题统一提亮，
 *   并且都和背景做对比度校验，避免出现“文字和背景太像”的主题色；
 * - onPrimary / onContainer 等前景色根据底色亮度自动选黑或白，保证按钮和选中态可读。
 */
fun buildColorScheme(seedArgb: Long, dark: Boolean): ColorScheme {
    val seedHsl = FloatArray(3)
    ColorUtils.colorToHSL(seedArgb.toInt(), seedHsl)
    val baseHue = seedHsl[0]
    val baseSat = seedHsl[1].coerceIn(0.30f, 0.92f)

    val background = if (dark) BackgroundDark else BackgroundLight
    val surface = if (dark) SurfaceDark else SurfaceLight
    val surfaceMuted = if (dark) SurfaceMutedDark else SurfaceMutedLight

    fun tone(hueOffset: Float, saturation: Float, lightness: Float): Color {
        val hue = ((baseHue + hueOffset) % 360f + 360f) % 360f
        return Color(
            ColorUtils.HSLToColor(
                floatArrayOf(
                    hue,
                    saturation.coerceIn(0f, 1f),
                    lightness.coerceIn(0f, 1f),
                ),
            ),
        )
    }

    fun luminance(color: Color): Double = ColorUtils.calculateLuminance(color.toArgb())

    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    fun readableOn(bg: Color): Color =
        if (contrast(Color.White, bg) >= contrast(Ink, bg)) Color.White else Ink

    /** 逐级调整亮度，直到该颜色与参考底色的对比度达到 minRatio。 */
    fun ensureContrast(color: Color, against: Color, minRatio: Double = 4.5): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        var candidate = color
        val darken = luminance(against) > 0.45
        var guard = 0
        while (contrast(candidate, against) < minRatio && guard < 30) {
            hsl[2] = (hsl[2] + if (darken) -0.03f else 0.03f).coerceIn(0.08f, 0.96f)
            candidate = Color(ColorUtils.HSLToColor(hsl))
            guard++
        }
        return candidate
    }

    // 强调色统一亮度：浅色主题压暗、深色主题提亮，并保证作为文字时与背景有足够对比。
    val emphasisLightness = if (dark) 0.74f else 0.38f
    val primary = ensureContrast(tone(0f, baseSat * 0.88f, emphasisLightness), surface)
    val secondary = ensureContrast(tone(36f, baseSat * 0.70f, emphasisLightness), surface)
    val tertiary = ensureContrast(tone(-42f, baseSat * 0.70f, emphasisLightness), surface)

    val primaryContainer = tone(0f, baseSat * 0.55f, if (dark) 0.30f else 0.90f)
    val secondaryContainer = tone(36f, baseSat * 0.45f, if (dark) 0.30f else 0.91f)
    val tertiaryContainer = tone(-42f, baseSat * 0.45f, if (dark) 0.30f else 0.90f)

    fun blend(base: Color, overlay: Color, ratio: Float): Color =
        Color(ColorUtils.blendARGB(base.toArgb(), overlay.toArgb(), ratio))

    return if (dark) {
        DarkColors.copy(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer),
            surfaceTint = primary,
            inversePrimary = tone(0f, baseSat * 0.85f, 0.40f),
            surfaceVariant = blend(surfaceMuted, tone(0f, baseSat * 0.6f, 0.42f), 0.10f),
        )
    } else {
        LightColors.copy(
            primary = primary,
            onPrimary = readableOn(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = readableOn(primaryContainer),
            secondary = secondary,
            onSecondary = readableOn(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = readableOn(secondaryContainer),
            tertiary = tertiary,
            onTertiary = readableOn(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = readableOn(tertiaryContainer),
            surfaceTint = primary,
            inversePrimary = tone(0f, baseSat * 0.85f, 0.72f),
            surfaceVariant = blend(surfaceMuted, tone(0f, baseSat * 0.5f, 0.55f), 0.08f),
        )
    }
}
