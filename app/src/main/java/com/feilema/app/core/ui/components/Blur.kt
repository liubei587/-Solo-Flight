package com.feilema.app.core.ui.components

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

/**
 * 背景模糊：Android 12+ 用 RenderEffect 真正模糊，低版本自动退化为无模糊。
 * Robolectric 的软件渲染不支持 RenderEffect，跳过它以免 JVM 测试崩溃。
 */
fun Modifier.premiumBlur(radius: Dp): Modifier {
    val isRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isRobolectric) {
        blur(radius)
    } else {
        this
    }
}
