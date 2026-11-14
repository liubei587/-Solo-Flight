package com.feilema.app.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * 数字滚动显示：数值变化时从旧值平滑滚到新值。
 */
@Composable
fun RollingNumber(
    value: Double,
    modifier: Modifier = Modifier,
    formatter: (Double) -> String = { it.toInt().toString() },
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    durationMillis: Int = 720,
    maxLines: Int = 1,
) {
    val target = value.toFloat()
    val animated = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (animated.value != target) {
            animated.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
            )
        }
    }
    Text(
        text = formatter(animated.value.toDouble()),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
    )
}
