package com.feilema.app.core.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.feilema.app.core.ui.theme.White
import com.feilema.app.core.ui.theme.hsvColor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * HSV 轮盘取色：点色相环选色相，滑杆调饱和度和明度。
 */
@Composable
fun ColorPickerDialog(
    initialColor: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV((initialColor ?: 0xFF2563EB.toLong()).toInt(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceAtLeast(0.45f)) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.55f)) }
    val preview = hsvColor(hue, saturation, brightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义主题色") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HueRing(
                    hue = hue,
                    selectedColor = preview,
                    modifier = Modifier.size(196.dp),
                    onHueChange = { hue = it },
                )
                Text(
                    "饱和度 ${(saturation * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    valueRange = 0.15f..1f,
                )
                Text(
                    "明度 ${(brightness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    valueRange = 0.25f..1f,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(preview, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(preview.toHex(), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(preview.toArgb().toLong() and 0xFFFFFFFFL) }) {
                Text("使用这个颜色")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun Color.toHex(): String = "#%06X".format(this.toArgb() and 0xFFFFFF)

@Composable
private fun HueRing(
    hue: Float,
    selectedColor: Color,
    modifier: Modifier,
    onHueChange: (Float) -> Unit,
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange(hueFromOffset(offset, size.width.toFloat(), size.height.toFloat()))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange(hueFromOffset(change.position, size.width.toFloat(), size.height.toFloat()))
                }
            },
    ) {
        val stroke = 20.dp.toPx()
        val radius = (minOf(size.width, size.height) - stroke) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val segments = 60
        for (i in 0 until segments) {
            drawArc(
                color = Color.hsv(i * (360f / segments), 0.85f, 1f),
                startAngle = i * (360f / segments) - 90f,
                sweepAngle = 360f / segments + 0.8f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        val angleRad = Math.toRadians((hue - 90f).toDouble())
        val handle = Offset(
            center.x + cos(angleRad).toFloat() * radius,
            center.y + sin(angleRad).toFloat() * radius,
        )
        drawCircle(
            color = White,
            radius = stroke * 0.5f + 1.dp.toPx(),
            center = handle,
            style = Stroke(width = 3.dp.toPx()),
        )
        drawCircle(color = selectedColor, radius = stroke * 0.5f - 1.dp.toPx(), center = handle)
    }
}

private fun hueFromOffset(offset: Offset, width: Float, height: Float): Float {
    val cx = width / 2f
    val cy = height / 2f
    val deg = Math.toDegrees(
        atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble()),
    ).toFloat()
    return ((deg + 90f) % 360f + 360f) % 360f
}
