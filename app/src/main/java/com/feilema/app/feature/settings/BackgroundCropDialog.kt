package com.feilema.app.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 选择背景图后的裁剪弹窗：
 * 固定成屏幕比例的取景框，拖动图片调整位置、双指缩放，确认后裁出选中区域。
 */
@Composable
internal fun BackgroundCropDialog(
    source: Bitmap,
    targetAspect: Float,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // 取景框比例 = 背景实际显示区域的宽高比（设置页用窗口根视图尺寸算好传入），
    // 这样裁出来的区域铺到背景上不会再被拉伸。
    val screenAspect = if (targetAspect > 0f) {
        targetAspect
    } else {
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
        } else {
            0.5f
        }
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "裁剪背景",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                        Text(
                            text = "拖动图片选择位置，双指缩放",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                }

                Spacer(Modifier.size(14.dp))

                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val maxHeight = maxHeight
                    val maxViewportWidth = maxWidth
                    val viewportHeight = min(
                        maxHeight.value,
                        maxViewportWidth.value / screenAspect,
                    ).dp
                    val viewportWidth = viewportHeight * screenAspect

                    Box(
                        modifier = Modifier
                            .size(viewportWidth, viewportHeight)
                            .clip(RoundedCornerShape(20.dp))
                            .background(scheme.surfaceVariant.copy(alpha = 0.35f))
                            .onSizeChanged { viewportSize = it }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    pan += panChange
                                }
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val viewport = Size(size.width, size.height)
                            val transform = coverCropDrawRect(
                                viewportWidth = viewport.width,
                                viewportHeight = viewport.height,
                                imageWidth = source.width,
                                imageHeight = source.height,
                                zoom = zoom,
                                panX = pan.x,
                                panY = pan.y,
                            )
                            drawImage(
                                image = source.asImageBitmap(),
                                dstOffset = IntOffset(
                                    transform.left.roundToInt(),
                                    transform.top.roundToInt(),
                                ),
                                dstSize = IntSize(
                                    transform.width.roundToInt().coerceAtLeast(1),
                                    transform.height.roundToInt().coerceAtLeast(1),
                                ),
                            )

                            // 1/3 网格 + 白色边框，帮助构图。
                            val grid = Color.White.copy(alpha = 0.32f)
                            for (i in 1..2) {
                                val x = size.width * i / 3f
                                val y = size.height * i / 3f
                                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            }
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.75f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                }

                Spacer(Modifier.size(16.dp))

                Text(
                    text = "裁出的区域会保存为应用内背景，并叠加主题色遮罩保证文字清晰。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )

                Spacer(Modifier.size(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            val viewport = Size(
                                viewportSize.width.toFloat(),
                                viewportSize.height.toFloat(),
                            )
                            if (viewport.width <= 0f || viewport.height <= 0f) {
                                onDismiss()
                                return@Button
                            }
                            val transform = coverCropDrawRect(
                                viewportWidth = viewport.width,
                                viewportHeight = viewport.height,
                                imageWidth = source.width,
                                imageHeight = source.height,
                                zoom = zoom,
                                panX = pan.x,
                                panY = pan.y,
                            )
                            val region = cropSourceRegion(
                                imageWidth = source.width,
                                imageHeight = source.height,
                                draw = transform,
                                viewportWidth = viewport.width,
                                viewportHeight = viewport.height,
                            )
                            val cropped = Bitmap.createBitmap(
                                source,
                                region.left,
                                region.top,
                                region.width,
                                region.height,
                            )
                            onCropped(downscaleForBackground(cropped))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("使用这张背景") }
                }
            }
        }
    }
}

/** 背景最多保存 1600px 长边，避免占内存过多。 */
private fun downscaleForBackground(bitmap: Bitmap, maxEdge: Int = 1600): Bitmap {
    val edge = max(bitmap.width, bitmap.height)
    if (edge <= maxEdge) return bitmap
    val ratio = maxEdge / edge.toFloat()
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
        (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
}

/** 读取用户选择的图片，按最大边采样，避免大图直接进内存。 */
internal fun decodeBackgroundForCrop(
    context: Context,
    uri: Uri,
    maxEdge: Int = 1600,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    }
}

/** 把裁剪结果存进应用私有目录，返回可直接持久化的绝对路径。
 *
 * 每次都用新的文件名：这样背景路径一定变化，界面才会重新解码新图片；
 * 写入成功后清掉旧背景文件，避免长期占用空间。
 */
internal fun saveBackgroundBitmap(context: Context, bitmap: Bitmap): String? = runCatching {
    val dir = context.filesDir
    val file = File(dir, "custom_background_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
    }
    dir.listFiles { item ->
        item.isFile &&
            item.name.startsWith("custom_background") &&
            item.name.endsWith(".jpg") &&
            item.name != file.name
    }?.forEach { it.delete() }
    file.absolutePath
}.getOrNull()
