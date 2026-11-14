package com.feilema.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CropGeometryTest {

    @Test
    fun centerCropKeepsViewportAspectAndCentersImage() {
        // 1600x1200 的横图放进 1080x2400 的竖屏取景框：按高度铺满，左右裁掉。
        val draw = coverCropDrawRect(
            viewportWidth = 1080f,
            viewportHeight = 2400f,
            imageWidth = 1600,
            imageHeight = 1200,
            zoom = 1f,
            panX = 0f,
            panY = 0f,
        )
        assertEquals(2f, draw.scale, 0.0001f)
        assertEquals(-1060f, draw.left, 0.5f)
        assertEquals(0f, draw.top, 0.5f)

        val region = cropSourceRegion(
            imageWidth = 1600,
            imageHeight = 1200,
            draw = draw,
            viewportWidth = 1080f,
            viewportHeight = 2400f,
        )
        // 竖直方向整张图都在取景框里，横向取中间 540px。
        assertEquals(0, region.top)
        assertEquals(1200, region.height)
        assertEquals(530, region.left)
        assertEquals(540, region.width)
        // 横向居中：选中区域的中心就是图片中心。
        assertEquals(800f, region.left + region.width / 2f, 1.5f)
        // 选中区域宽高比 = 取景框宽高比（0.45）。
        assertEquals(0.45f, region.width.toFloat() / region.height, 0.01f)
    }

    @Test
    fun zoomShrinksSourceRegion() {
        val base = coverCropDrawRect(1080f, 2400f, 1600, 1200, zoom = 1f, panX = 0f, panY = 0f)
        val zoomed = coverCropDrawRect(1080f, 2400f, 1600, 1200, zoom = 2f, panX = 0f, panY = 0f)
        val baseRegion = cropSourceRegion(1600, 1200, base, 1080f, 2400f)
        val zoomedRegion = cropSourceRegion(1600, 1200, zoomed, 1080f, 2400f)
        assertTrue(
            "zoom width: base=${baseRegion.width} zoomed=${zoomedRegion.width}",
            abs(zoomedRegion.width - baseRegion.width / 2f) <= 2f,
        )
        assertTrue(
            "zoom height: base=${baseRegion.height} zoomed=${zoomedRegion.height}",
            abs(zoomedRegion.height - baseRegion.height / 2f) <= 2f,
        )
    }

    @Test
    fun panIsClampedInsideImage() {
        val draw = coverCropDrawRect(
            viewportWidth = 1080f,
            viewportHeight = 2400f,
            imageWidth = 1600,
            imageHeight = 1200,
            zoom = 1.5f,
            panX = 100_000f,
            panY = 100_000f,
        )
        // 平移被限制住，图片边缘不会离开取景框。
        assertEquals(0f, draw.left, 0.5f)
        assertEquals(0f, draw.top, 0.5f)
        val region = cropSourceRegion(1600, 1200, draw, 1080f, 2400f)
        assertEquals(0, region.left)
        assertEquals(0, region.top)
        assertTrue(region.left + region.width <= 1600)
        assertTrue(region.top + region.height <= 1200)
    }

    @Test
    fun sourceRegionAlwaysInsideImageAndKeepsAspect() {
        val viewportWidth = 1080f
        val viewportHeight = 2400f
        val images = listOf(1600 to 1200, 1200 to 1600, 3000 to 2000, 800 to 800)
        val zooms = listOf(1f, 1.5f, 2f, 4f)
        val pans = listOf(-500f to 0f, 0f to 500f, 300f to -300f, 10_000f to 10_000f)
        for ((imageWidth, imageHeight) in images) {
            for (zoom in zooms) {
                for ((panX, panY) in pans) {
                    val draw = coverCropDrawRect(
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        zoom = zoom,
                        panX = panX,
                        panY = panY,
                    )
                    val region = cropSourceRegion(
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        draw = draw,
                        viewportWidth = viewportWidth,
                        viewportHeight = viewportHeight,
                    )
                    assertTrue("left ${region.left} for $imageWidth x $imageHeight", region.left >= 0)
                    assertTrue("top ${region.top} for $imageWidth x $imageHeight", region.top >= 0)
                    assertTrue(
                        "width ${region.width} for $imageWidth x $imageHeight",
                        region.left + region.width <= imageWidth,
                    )
                    assertTrue(
                        "height ${region.height} for $imageWidth x $imageHeight",
                        region.top + region.height <= imageHeight,
                    )
                    val expectedAspect = viewportWidth / viewportHeight
                    val actualAspect = region.width.toFloat() / region.height
                    assertTrue(
                        "aspect $actualAspect vs $expectedAspect for $imageWidth x $imageHeight",
                        abs(actualAspect - expectedAspect) < 0.02f,
                    )
                }
            }
        }
    }
}
