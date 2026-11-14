package com.feilema.app.data.preferences

import android.content.Context
import com.feilema.app.core.ui.theme.ThemeMode
import com.feilema.app.core.ui.theme.ThemePalette
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户的外观设置：明暗模式、主题色（预设或自定义）、自定义背景图。
 */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.BLUE,
    val customColor: Long? = null,
    val backgroundUri: String? = null,
) {
    val seedColor: Long get() = customColor ?: palette.seed

    /** 默认海蓝继续使用手工调过的默认配色，避免默认观感变化。 */
    val isDefaultBlue: Boolean get() = customColor == null && palette == ThemePalette.BLUE

    /** 传给 FeiLeMaTheme 的种子色：默认海蓝返回 null。 */
    val themeSeed: Long? get() = if (isDefaultBlue) null else seedColor
}

/**
 * 外观偏好。字段不多，用 SharedPreferences 就够。
 */
@Singleton
class ThemePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    fun setMode(mode: ThemeMode) = update { it.copy(mode = mode) }

    fun setPalette(palette: ThemePalette) = update { it.copy(palette = palette, customColor = null) }

    fun setCustomColor(color: Long?) = update { it.copy(customColor = color) }

    fun setBackgroundUri(uri: String?) = update { it.copy(backgroundUri = uri) }

    private fun update(transform: (ThemeSettings) -> ThemeSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_MODE, next.mode.storageKey)
            .putString(KEY_PALETTE, next.palette.name)
            .putLong(KEY_CUSTOM_COLOR, next.customColor ?: NO_COLOR)
            .putString(KEY_BACKGROUND, next.backgroundUri)
            .apply()
        _settings.value = next
    }

    private fun read(): ThemeSettings {
        val custom = prefs.getLong(KEY_CUSTOM_COLOR, NO_COLOR)
        return ThemeSettings(
            mode = ThemeMode.fromStorage(prefs.getString(KEY_MODE, null)),
            palette = ThemePalette.fromStorage(prefs.getString(KEY_PALETTE, null)),
            customColor = custom.takeIf { it != NO_COLOR },
            backgroundUri = prefs.getString(KEY_BACKGROUND, null),
        )
    }

    private companion object {
        const val PREFS_NAME = "theme_prefs"
        const val KEY_MODE = "theme_mode"
        const val KEY_PALETTE = "theme_palette"
        const val KEY_CUSTOM_COLOR = "theme_custom_color"
        const val KEY_BACKGROUND = "theme_background_uri"
        const val NO_COLOR = -1L
    }
}
