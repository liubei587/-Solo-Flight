package com.feilema.app.core.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 应用底色：
 * 没有自定义背景时使用主题色渐变；选了图片就直接铺图片，最后压一层主题色遮罩，
 * 保证任何背景上文字都清晰可读。
 */
@Composable
fun AppBackground(
    backgroundUri: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, backgroundUri) {
        value = if (backgroundUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { decodeBackgroundImage(context, backgroundUri) }.getOrNull()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // 裁剪结果的宽高比已按背景显示区域计算；这里用 Crop 保证即使窗口尺寸/比例
                // 有细微差异也不会拉伸（最多裁掉边缘几个像素）。
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.primary.copy(alpha = if (dark) 0.20f else 0.10f),
                                scheme.background,
                                scheme.background,
                            ),
                        ),
                    ),
            )
        }
        // 遮罩：保证深色/浅色主题下文字都清晰。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background.copy(alpha = if (dark) 0.58f else 0.50f)),
        )
        content()
    }
}

private fun decodeBackgroundImage(context: Context, source: String): ImageBitmap? {
    val file = File(
        if (source.startsWith("file://")) Uri.parse(source).path ?: source else source,
    )
    if (file.isFile) {
        return decodeFile(file)?.asImageBitmap()
    }
    val resolver = context.contentResolver
    val parsed = Uri.parse(source)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null
    return bitmap.asImageBitmap()
}

private fun decodeFile(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}
