package com.feilema.app.data.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.feilema.app.core.ui.theme.ThemePalette
import com.feilema.app.data.preferences.ThemeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 桌面图标主题色切换。
 *
 * Android 不能直接给已安装的图标换色，所以为每套配色准备一个 activity-alias，
 * 切换主题色时启用目标 alias、禁用其他 alias。alias 的进程内状态是持久的，
 * 需要 DONT_KILL_APP，用户下次回桌面就能看到新图标。
 */
@Singleton
class LauncherIconController @Inject constructor(@ApplicationContext private val context: Context) {

    fun apply(settings: ThemeSettings) {
        val target = if (settings.customColor != null) ALIAS_COLORFUL else aliasFor(settings.palette)
        val packageManager = context.packageManager
        // 先启用目标再关闭其他，避免桌面上短暂出现“没有图标”的状态
        runCatching {
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, target),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        ALIASES.filter { it != target }.forEach { alias ->
            runCatching {
                packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    private fun aliasFor(palette: ThemePalette): String = when (palette) {
        ThemePalette.BLUE -> ALIAS_BLUE
        ThemePalette.INDIGO -> ALIAS_INDIGO
        ThemePalette.VIOLET -> ALIAS_VIOLET
        ThemePalette.TEAL -> ALIAS_TEAL
        ThemePalette.GREEN -> ALIAS_GREEN
        ThemePalette.AMBER -> ALIAS_AMBER
        ThemePalette.ROSE -> ALIAS_ROSE
    }

    private companion object {
        const val ALIAS_BLUE = "com.feilema.app.LauncherBlue"
        const val ALIAS_INDIGO = "com.feilema.app.LauncherIndigo"
        const val ALIAS_VIOLET = "com.feilema.app.LauncherViolet"
        const val ALIAS_TEAL = "com.feilema.app.LauncherTeal"
        const val ALIAS_GREEN = "com.feilema.app.LauncherGreen"
        const val ALIAS_AMBER = "com.feilema.app.LauncherAmber"
        const val ALIAS_ROSE = "com.feilema.app.LauncherRose"
        const val ALIAS_COLORFUL = "com.feilema.app.LauncherColorful"

        val ALIASES = listOf(
            ALIAS_BLUE,
            ALIAS_INDIGO,
            ALIAS_VIOLET,
            ALIAS_TEAL,
            ALIAS_GREEN,
            ALIAS_AMBER,
            ALIAS_ROSE,
            ALIAS_COLORFUL,
        )
    }
}
