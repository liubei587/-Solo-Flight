package com.feilema.app.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feilema.app.core.ui.components.RollingNumber
import com.feilema.app.domain.usecase.stats.BoxPlotSummary
import com.feilema.app.domain.usecase.stats.TrendPoint
import java.util.Locale

/** 折线趋势图：近 N 天的每日次数，带平均值虚线和描线动画。 */
@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    average: Double,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val averageColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val maxValue = (points.maxOfOrNull { it.count }?.toDouble() ?: 0.0)
        .coerceAtLeast(average)
        .coerceAtLeast(1.0)

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val left = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 12.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val chartWidth = (right - left).coerceAtLeast(1f)
            val chartHeight = (bottom - top).coerceAtLeast(1f)

            repeat(3) { index ->
                val y = top + chartHeight * (index + 1) / 4f
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            }

            val step = if (points.size > 1) chartWidth / (points.size - 1) else 0f
            fun xAt(index: Int): Float = left + step * index
            fun yAt(value: Float): Float =
                bottom - (value / maxValue.toFloat()).coerceIn(0f, 1f) * chartHeight

            val areaPath = Path().apply {
                moveTo(left, bottom)
                points.forEachIndexed { index, point ->
                    lineTo(xAt(index), yAt(point.count * progress.value))
                }
                lineTo(right, bottom)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0.02f)),
                ),
            )

            val averageY = yAt(average.toFloat())
            drawLine(
                color = averageColor.copy(alpha = 0.75f),
                start = Offset(left, averageY),
                end = Offset(right, averageY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )

            val linePath = Path()
            points.forEachIndexed { index, point ->
                val x = xAt(index)
                val y = yAt(point.count * progress.value)
                if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )

            points.forEachIndexed { index, point ->
                if (point.count > 0) {
                    drawCircle(
                        color = dotColor,
                        radius = 3.dp.toPx(),
                        center = Offset(xAt(index), yAt(point.count * progress.value)),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.size > 2) {
                Text(points[points.size / 2].label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "平均 ${formatOneDecimal(average)} 次",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "最高 ${points.maxOf { it.count }} 次",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 柱状图：时段 0-23 点、周内周一到周日。柱子高度有成长动画。 */
@Composable
fun AnimatedBarChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (values.isEmpty()) return
    val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEach { value ->
                val target = if (value <= 0) 0.02f else (value.toFloat() / maxValue).coerceIn(0.05f, 1f)
                val progress = remember { Animatable(0f) }
                LaunchedEffect(target) {
                    progress.animateTo(target, animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress.value)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (value <= 0) barColor.copy(alpha = 0.16f)
                                else barColor.copy(alpha = 0.82f),
                            ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = if (index % labelEvery == 0) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 箱线图：近 N 天每日次数的分布，离群点单独标红。 */
@Composable
fun BoxPlotChart(
    box: BoxPlotSummary,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val outlierColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(box) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    val alpha = progress.value.coerceIn(0f, 1f)
    val maxValue = maxOf(box.max, box.outliers.maxOrNull() ?: 0.0, 1.0)

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val left = 20.dp.toPx()
            val right = size.width - 20.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val chartHeight = (bottom - top).coerceAtLeast(1f)
            val centerX = (left + right) / 2f
            fun yAt(value: Double): Float =
                bottom - (value / maxValue).toFloat().coerceIn(0f, 1f) * chartHeight

            drawLine(gridColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1f)

            val boxWidth = 68.dp.toPx()
            val boxLeft = centerX - boxWidth / 2f
            drawLine(
                color = color.copy(alpha = 0.55f * alpha),
                start = Offset(centerX, yAt(box.min)),
                end = Offset(centerX, yAt(box.max)),
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(centerX - 14.dp.toPx(), yAt(box.min)),
                end = Offset(centerX + 14.dp.toPx(), yAt(box.min)),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(centerX - 14.dp.toPx(), yAt(box.max)),
                end = Offset(centerX + 14.dp.toPx(), yAt(box.max)),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val topY = yAt(box.q3)
            val bottomY = yAt(box.q1)
            val boxHeight = (bottomY - topY).coerceAtLeast(2f)
            drawRoundRect(
                color = color.copy(alpha = 0.18f * alpha),
                topLeft = Offset(boxLeft, topY),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = color.copy(alpha = 0.85f * alpha),
                topLeft = Offset(boxLeft, topY),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(boxLeft, yAt(box.median)),
                end = Offset(boxLeft + boxWidth, yAt(box.median)),
                strokeWidth = 3.dp.toPx(),
            )

            box.outliers.forEachIndexed { index, value ->
                val direction = if (index % 2 == 0) 1f else -1f
                drawCircle(
                    color = outlierColor.copy(alpha = 0.85f * alpha),
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX + direction * (boxWidth / 2f + 16.dp.toPx()), yAt(value)),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BoxStat("最小", box.min)
            BoxStat("Q1", box.q1)
            BoxStat("中位", box.median)
            BoxStat("Q3", box.q3)
            BoxStat("最大", box.max)
        }
        Text(
            text = buildString {
                append("样本 ${box.sampleSize} 个有记录日")
                if (box.outliers.isNotEmpty()) append(" · 离群 ${box.outliers.size} 个")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BoxStat(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            formatOneDecimal(value),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 指标小卡：标签 + 大数字（可滚动）+ 说明，可选环比箭头。 */
@Composable
fun MetricCard(
    label: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    caption: String? = null,
    change: Double? = null,
    rollingValue: Double? = null,
    rollingFormatter: (Double) -> String = { formatOneDecimal(it) },
) {
    Card(
        modifier = modifier.heightIn(min = 100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val valueModifier = Modifier.weight(1f, fill = false)
                if (rollingValue != null) {
                    RollingNumber(
                        value = rollingValue,
                        modifier = valueModifier,
                        formatter = rollingFormatter,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    val text = value ?: "—"
                    Text(
                        text,
                        modifier = valueModifier,
                        style = if (text.length > 7) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (change != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (change >= 0) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (change >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 环比环形图：外圈表示变动幅度（±100% 充满），中心显示百分比。 */
@Composable
fun ChangeRingCard(
    changeRatio: Double?,
    previousCount: Int,
    modifier: Modifier = Modifier,
) {
    val ringColor = when {
        changeRatio == null -> MaterialTheme.colorScheme.onSurfaceVariant
        changeRatio >= 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val target = changeRatio
        ?.let { (kotlin.math.abs(it).coerceIn(0.0, 2.0) / 2.0).toFloat() }
        ?: 0f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        progress.animateTo(target, animationSpec = tween(800, easing = FastOutSlowInEasing))
    }
    Card(
        modifier = modifier.heightIn(min = 100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "环比",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(66.dp)) {
                    val stroke = 7.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (changeRatio != null) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.value,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (changeRatio == null) "—" else formatChangePercent(changeRatio),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ringColor,
                    )
                    Text(
                        "vs 上期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "上一周期 $previousCount 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatChangePercent(ratio: Double): String =
    String.format(Locale.CHINA, "%+.0f%%", ratio * 100)

internal fun formatOneDecimal(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value)
