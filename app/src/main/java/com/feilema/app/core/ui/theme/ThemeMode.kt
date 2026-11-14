package com.feilema.app.core.ui.theme

/**
 * 用户可选的明暗模式。默认跟随系统，也可以在设置里固定成浅色或深色。
 */
enum class ThemeMode(val storageKey: String, val label: String) {
    SYSTEM(storageKey = "system", label = "跟随系统"),
    LIGHT(storageKey = "light", label = "浅色"),
    DARK(storageKey = "dark", label = "深色"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}
