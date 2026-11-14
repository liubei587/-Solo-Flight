package com.feilema.app.core.ui.theme

/**
 * 主题色预选色系。页面配色和桌面图标都会跟着这里的选择变化。
 */
enum class ThemePalette(val label: String, val seed: Long) {
    BLUE("海蓝", 0xFF2563EB),
    INDIGO("靛青", 0xFF4F46E5),
    VIOLET("紫罗兰", 0xFF7C3AED),
    TEAL("青碧", 0xFF0D9488),
    GREEN("森绿", 0xFF16A34A),
    AMBER("琥珀", 0xFFD97706),
    ROSE("玫瑰", 0xFFE11D48),
    ;

    companion object {
        fun fromStorage(value: String?): ThemePalette =
            entries.firstOrNull { it.name == value } ?: BLUE
    }
}

/**
 * 当前选中的主题色：要么是预选色系，要么是一枚自定义颜色（ARGB）。
 */
data class ThemeColorSetting(
    val palette: ThemePalette = ThemePalette.BLUE,
    val customColor: Long? = null,
) {
    val seedColor: Long get() = customColor ?: palette.seed

    /** 默认海蓝继续使用手工调过的配色，避免默认观感变化。 */
    val isDefaultBlue: Boolean get() = customColor == null && palette == ThemePalette.BLUE
}

/** 自定义取色的预览色，用 HSV 生成。 */
fun hsvColor(hue: Float, saturation: Float, value: Float): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color.hsv(
        hue = ((hue % 360f) + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f),
    )
