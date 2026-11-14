package com.feilema.app.feature.settings

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 裁剪取景框里图片的绘制矩形（单位 px）。
 * 图片始终完整覆盖取景框（ContentScale.Crop 的语义）。
 */
internal data class CropDrawRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val scale: Float,
)

/** 裁剪结果在原图中的像素区域。 */
internal data class CropSourceRegion(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * 计算图片在 [viewportWidth] × [viewportHeight] 取景框内的绘制位置与缩放。
 * [zoom] 为 1 时刚好铺满取景框，[panX]/[panY] 为拖动位移（px），会被限制在图片边界内。
 */
internal fun coverCropDrawRect(
    viewportWidth: Float,
    viewportHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
): CropDrawRect {
    require(viewportWidth > 0f && viewportHeight > 0f) { "viewport must be positive" }
    require(imageWidth > 0 && imageHeight > 0) { "image must be positive" }
    val baseScale = max(
        viewportWidth / imageWidth.toFloat(),
        viewportHeight / imageHeight.toFloat(),
    )
    val scale = baseScale * zoom.coerceIn(1f, 4f)
    val drawWidth = imageWidth * scale
    val drawHeight = imageHeight * scale
    val left = ((viewportWidth - drawWidth) / 2f + panX)
        .coerceIn(viewportWidth - drawWidth, 0f)
    val top = ((viewportHeight - drawHeight) / 2f + panY)
        .coerceIn(viewportHeight - drawHeight, 0f)
    return CropDrawRect(
        left = left,
        top = top,
        width = drawWidth,
        height = drawHeight,
        scale = scale,
    )
}

/**
 * 取景框当前可见的图片区域等于「取景框尺寸 ÷ 缩放比例」，
 * 从绘制矩形左上角反推源图偏移。这里必须用取景框尺寸而不是绘制尺寸，
 * 否则会裁出比预览更大的区域，显示时再被居中裁切，导致选中的位置对不上。
 */
internal fun cropSourceRegion(
    imageWidth: Int,
    imageHeight: Int,
    draw: CropDrawRect,
    viewportWidth: Float,
    viewportHeight: Float,
): CropSourceRegion {
    require(draw.scale > 0f) { "scale must be positive" }
    val left = (-draw.left / draw.scale).roundToInt().coerceIn(0, imageWidth - 1)
    val top = (-draw.top / draw.scale).roundToInt().coerceIn(0, imageHeight - 1)
    val width = (viewportWidth / draw.scale).roundToInt().coerceIn(1, imageWidth - left)
    val height = (viewportHeight / draw.scale).roundToInt().coerceIn(1, imageHeight - top)
    return CropSourceRegion(left = left, top = top, width = width, height = height)
}
