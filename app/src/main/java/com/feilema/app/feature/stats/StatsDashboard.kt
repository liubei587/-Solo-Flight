package com.feilema.app.feature.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.feilema.app.core.ui.components.RollingNumber
import com.feilema.app.domain.model.CardSize
import com.feilema.app.domain.model.GridRect
import com.feilema.app.domain.model.StatsCard
import com.feilema.app.domain.model.StatsLayout
import com.feilema.app.domain.model.computeGridSlots
import com.feilema.app.domain.model.freeDropRect
import com.feilema.app.domain.model.resolveSlots
import com.feilema.app.domain.model.withCardPosition
import com.feilema.app.domain.model.withDragged
import com.feilema.app.domain.usecase.stats.BoxPlotSummary
import com.feilema.app.domain.usecase.stats.StatsPageData
import com.feilema.app.domain.usecase.stats.StatsRange
import com.feilema.app.domain.usecase.stats.TrendPoint
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.min

/** 两列网格：所有卡片都对齐在 2 列的网格里，1×2 / 2×2 会占满整行或整块。 */
private const val GRID_COLUMNS = 2
private val GRID_GAP = 10.dp
private val CELL_HEIGHT = 118.dp

/**
 * 图表入场动画的触发信号：统计范围切换、重新进入页面、增删卡片都会变，
 * 图表读到新 key 后从 0 重新长出来。
 */
private val LocalChartAnimationKey = compositionLocalOf<Any?> { null }

/** 图表统一的入场进度：0 → 1，首次显示和切换统计范围时都会播一遍。 */
@Composable
private fun rememberChartProgress(): Float {
    val animationKey = LocalChartAnimationKey.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }
    return progress.value
}

/**
 * 可自适应尺寸的统计卡片网格。
 *
 * - 非编辑态：长按任意卡片进入编辑模式；
 * - 编辑态：长按拖动卡片排序，其他卡片用弹簧动画实时避让（水波式位移），
 *   拖动中的卡片位置与目标位置之间显示半透明虚线落位框；
 * - 卡片右上角提供改大小/删除按钮。
 */
@Composable
internal fun StatsCardGrid(
    layout: StatsLayout,
    editing: Boolean,
    page: StatsPageData,
    scrollState: LazyListState,
    /** 图表入场动画的触发 key：切统计范围、重新进入页面时传入新值即可重播动画。 */
    chartAnimationKey: Any? = null,
    onEnterEdit: () -> Unit,
    onMove: (StatsCard, Int) -> Unit,
    onPlace: (StatsCard, GridRect) -> Unit,
    onResize: (StatsCard) -> Unit,
    onRemove: (StatsCard) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val pxPerDp = density.density

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - GRID_GAP) / GRID_COLUMNS
        // 调整大小后 layout 里保存了位置（允许留空），其他情况用紧凑排布。
        val baseSlots = remember(layout) {
            layout.resolveSlots() ?: computeGridSlots(layout.order) { layout.sizeOf(it) }
        }

        var dragCard by remember { mutableStateOf<StatsCard?>(null) }
        var hoverIndex by remember { mutableStateOf<Int?>(null) }
        var hoverSlot by remember { mutableStateOf<GridRect?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        var dragWindowPointer by remember { mutableStateOf(Offset.Zero) }
        var grabOffset by remember { mutableStateOf(Offset.Zero) }
        var gridOrigin by remember { mutableStateOf(Offset.Zero) }
        var settling by remember { mutableStateOf(false) }
        var autoScrollDir by remember { mutableIntStateOf(0) }
        // 记录每张卡片当前在窗口中的真实位置：拖动开始时用它对齐手指，
        // 避免卡片在入场动画/弹簧动画途中被长按时跳到别处。
        val cardPositions = remember { mutableStateMapOf<StatsCard, Offset>() }

        // 拖动落点有两种：拖到别的卡片上 → 交换两张卡片的位置；
        // 拖到空出来的格子上 → 直接放到那里（其他卡片不动，允许留空格）。
        val previewLayout = remember(layout, dragCard, hoverIndex, hoverSlot) {
            val card = dragCard
            when {
                card == null -> layout
                hoverIndex != null -> layout.withDragged(card, hoverIndex!!)
                hoverSlot != null -> layout.withCardPosition(card, hoverSlot!!)
                else -> layout
            }
        }
        val renderSlots = remember(previewLayout, baseSlots) {
            if (dragCard == null) {
                baseSlots
            } else {
                previewLayout.resolveSlots()
                    ?: computeGridSlots(previewLayout.order) { previewLayout.sizeOf(it) }
            }
        }
        val placeholderSlot = if (dragCard != null) hoverSlot ?: renderSlots[dragCard] else null
        val maxRow = maxOf(
            baseSlots.values.maxOfOrNull { it.row + it.rows } ?: 0,
            renderSlots.values.maxOfOrNull { it.row + it.rows } ?: 0,
        )
        val gridHeight = if (maxRow == 0) 0.dp else (CELL_HEIGHT + GRID_GAP) * maxRow - GRID_GAP

        val screenHeightPx = configuration.screenHeightDp * pxPerDp
        val topEdgePx = 170f * pxPerDp
        val bottomEdgePx = screenHeightPx - 210f * pxPerDp

        fun slotCenter(slot: GridRect): Offset = Offset(
            x = ((cellWidth + GRID_GAP) * slot.column).value * pxPerDp +
                (cellWidth * slot.columns + GRID_GAP * (slot.columns - 1)).value * pxPerDp / 2f,
            y = ((CELL_HEIGHT + GRID_GAP) * slot.row).value * pxPerDp +
                (CELL_HEIGHT * slot.rows + GRID_GAP * (slot.rows - 1)).value * pxPerDp / 2f,
        )

        fun nearestIndex(pointer: Offset): Int {
            val nearest = baseSlots.entries.minByOrNull { (_, slot) ->
                val center = slotCenter(slot)
                hypot(pointer.x - center.x, pointer.y - center.y)
            }?.key ?: return 0
            return layout.order.indexOf(nearest).coerceAtLeast(0)
        }

        /**
         * 按被拖卡片当前左上角（不是手指位置）计算「自由落点」：
         * 只有落在空出来的格子上才返回，落在已有卡片上时返回 null，
         * 改用「与目标卡片交换位置」的语义。
         */
        fun freeDropAt(): GridRect? {
            val card = dragCard ?: return null
            val column = (dragOffset.x / (cellWidth + GRID_GAP).value).roundToInt()
            val row = (dragOffset.y / (CELL_HEIGHT + GRID_GAP).value).roundToInt()
            return layout.freeDropRect(card, column, row)
        }

        // 拖动时卡片位置 = 手指窗口坐标 - 网格在窗口中的位置 - 抓取偏移。
        // 列表自动滚动会改变网格位置，这里重新计算可以保证卡片始终跟着手指。
        fun syncDrag() {
            val card = dragCard ?: return
            val pointerInGrid = dragWindowPointer - gridOrigin
            dragOffset = Offset(
                (pointerInGrid.x - grabOffset.x) / pxPerDp,
                (pointerInGrid.y - grabOffset.y) / pxPerDp,
            )
            // 空位优先：能放进空格就显示空格落位框；否则才交换到最近的卡片。
            val drop = freeDropAt()
            if (drop != null) {
                hoverSlot = drop
                hoverIndex = null
            } else {
                hoverSlot = null
                hoverIndex = nearestIndex(pointerInGrid)
            }
            val size = layout.sizeOf(card)
            val cardTopPx = dragWindowPointer.y - grabOffset.y
            val cardHeightPx = CELL_HEIGHT.value * size.rows * pxPerDp +
                GRID_GAP.value * (size.rows - 1) * pxPerDp
            autoScrollDir = when {
                cardTopPx < topEdgePx -> -1
                cardTopPx + cardHeightPx > bottomEdgePx -> 1
                else -> 0
            }
        }

        LaunchedEffect(autoScrollDir, dragCard) {
            while (dragCard != null && autoScrollDir != 0) {
                val consumed = scrollState.scrollBy(autoScrollDir * 12f * pxPerDp)
                if (consumed == 0f) {
                    autoScrollDir = 0
                    break
                }
                delay(16)
                syncDrag()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .onGloballyPositioned { coordinates ->
                    gridOrigin = coordinates.positionInWindow()
                },
        ) {
            if (editing && dragCard != null && placeholderSlot != null) {
                LandingPlaceholder(slot = placeholderSlot, cellWidth = cellWidth)
            }

            layout.order.forEach { card ->
                val slot = renderSlots[card] ?: return@forEach
                key(card) {
                    CompositionLocalProvider(LocalChartAnimationKey provides chartAnimationKey) {
                    val targetX = (cellWidth + GRID_GAP) * slot.column
                    val targetY = (CELL_HEIGHT + GRID_GAP) * slot.row
                    val targetWidth = cellWidth * slot.columns + GRID_GAP * (slot.columns - 1)
                    val targetHeight = CELL_HEIGHT * slot.rows + GRID_GAP * (slot.rows - 1)
                    val isDragging = dragCard == card

                    val animatedX by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "card-x-$card",
                    )
                    val animatedY by animateDpAsState(
                        targetValue = targetY,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "card-y-$card",
                    )
                    val animatedWidth by animateDpAsState(
                        targetValue = targetWidth,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "card-w-$card",
                    )
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "card-h-$card",
                    )

                    val x = if (isDragging) dragOffset.x.dp else animatedX
                    val y = if (isDragging) dragOffset.y.dp else animatedY

                    DashboardCard(
                        card = card,
                        index = layout.order.indexOf(card),
                        slot = slot,
                        editing = editing,
                        dragging = isDragging,
                        page = page,
                        onResize = { onResize(card) },
                        onRemove = { onRemove(card) },
                        modifier = Modifier
                            .size(animatedWidth, animatedHeight)
                            .offsetDp(x, y)
                            .onGloballyPositioned { coordinates ->
                                cardPositions[card] = coordinates.positionInWindow()
                            }
                            .zIndex(if (isDragging) 20f else 1f)
                            .pointerInput(card, editing, settling) {
                                if (!editing) {
                                    detectTapGestures(onLongPress = { onEnterEdit() })
                                } else if (!settling) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { localOffset ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragCard = card
                                            hoverIndex = layout.order.indexOf(card)
                                            grabOffset = localOffset
                                            // 用卡片当前真实窗口位置对齐手指，
                                            // 而不是用目标槽位（入场/弹簧动画期间两者可能不同）。
                                            val cardWindow = cardPositions[card] ?: Offset(
                                                x = gridOrigin.x + targetX.value * pxPerDp,
                                                y = gridOrigin.y + targetY.value * pxPerDp,
                                            )
                                            dragWindowPointer = cardWindow + localOffset
                                            dragOffset = Offset(
                                                (cardWindow.x - gridOrigin.x) / pxPerDp,
                                                (cardWindow.y - gridOrigin.y) / pxPerDp,
                                            )
                                            autoScrollDir = 0
                                            hoverSlot = null
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragWindowPointer += amount
                                            syncDrag()
                                        },
                                        onDragEnd = {
                                            autoScrollDir = 0
                                            val cardNow = dragCard
                                            val target = hoverIndex
                                            val freeTarget = hoverSlot
                                            if (cardNow != null && (target != null || freeTarget != null)) {
                                                val startOffset = dragOffset
                                                val endLayout = if (freeTarget != null) {
                                                    layout.withCardPosition(cardNow, freeTarget)
                                                } else {
                                                    layout.withDragged(cardNow, target!!)
                                                }
                                                val endSlots = endLayout.resolveSlots()
                                                    ?: computeGridSlots(endLayout.order) { endLayout.sizeOf(it) }
                                                val targetSlot = endSlots[cardNow]
                                                val endOffset = if (targetSlot != null) {
                                                    Offset(
                                                        (cellWidth + GRID_GAP).value * targetSlot.column,
                                                        (CELL_HEIGHT + GRID_GAP).value * targetSlot.row,
                                                    )
                                                } else {
                                                    startOffset
                                                }
                                                settling = true
                                                scope.launch {
                                                    animate(
                                                        initialValue = 0f,
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMediumLow,
                                                        ),
                                                    ) { progress, _ ->
                                                        dragOffset = Offset(
                                                            startOffset.x + (endOffset.x - startOffset.x) * progress,
                                                            startOffset.y + (endOffset.y - startOffset.y) * progress,
                                                        )
                                                    }
                                                    if (freeTarget != null) {
                                                        onPlace(cardNow, freeTarget)
                                                    } else {
                                                        onMove(cardNow, target!!)
                                                    }
                                                    dragCard = null
                                                    dragOffset = Offset.Zero
                                                    hoverIndex = null
                                                    hoverSlot = null
                                                    settling = false
                                                }
                                            } else {
                                                dragCard = null
                                                dragOffset = Offset.Zero
                                                hoverIndex = null
                                                hoverSlot = null
                                            }
                                        },
                                        onDragCancel = {
                                            autoScrollDir = 0
                                            dragCard = null
                                            dragOffset = Offset.Zero
                                            hoverIndex = null
                                            hoverSlot = null
                                        },
                                    )
                                }
                            },
                    )
                    }
                }
            }
        }
    }
}

private fun Modifier.offsetDp(x: Dp, y: Dp): Modifier = this.offset(x = x, y = y)

@Composable
private fun LandingPlaceholder(slot: GridRect, cellWidth: Dp) {
    val scheme = MaterialTheme.colorScheme
    val x by animateDpAsState(
        targetValue = (cellWidth + GRID_GAP) * slot.column,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "placeholder-x",
    )
    val y by animateDpAsState(
        targetValue = (CELL_HEIGHT + GRID_GAP) * slot.row,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "placeholder-y",
    )
    val width by animateDpAsState(
        targetValue = cellWidth * slot.columns + GRID_GAP * (slot.columns - 1),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "placeholder-w",
    )
    val height by animateDpAsState(
        targetValue = CELL_HEIGHT * slot.rows + GRID_GAP * (slot.rows - 1),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "placeholder-h",
    )
    Box(
        modifier = Modifier
            .offsetDp(x, y)
            .size(width, height)
            .zIndex(0.5f)
            .drawBehind {
                val corner = 20.dp.toPx()
                drawRoundRect(
                    color = scheme.primary.copy(alpha = 0.08f),
                    cornerRadius = CornerRadius(corner),
                )
                drawRoundRect(
                    color = scheme.primary.copy(alpha = 0.55f),
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            },
    )
}

@Composable
private fun DashboardCard(
    card: StatsCard,
    index: Int,
    slot: GridRect,
    editing: Boolean,
    dragging: Boolean,
    page: StatsPageData,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val jiggleTransition = rememberInfiniteTransition(label = "jiggle-$card")
    val jigglePhase by jiggleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "jiggle-phase-$card",
    )
    val jiggle = if (editing && !dragging) {
        sin(jigglePhase * 2f * PI.toFloat() + index * 0.9f) * 1.4f
    } else {
        0f
    }
    val scale by animateFloatAsState(
        targetValue = when {
            dragging -> 0.93f
            editing -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card-scale-$card",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            dragging -> 26.dp
            editing -> 12.dp
            else -> 8.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card-elev-$card",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = jiggle
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape),
    ) {
        FrostedBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = if (editing) 34.dp else 12.dp,
                    bottom = 10.dp,
                ),
        ) {
            StatCardContent(
                card = card,
                slot = slot,
                page = page,
            )
        }
        if (editing) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OverlayIconButton(description = "改变卡片大小", onClick = onResize) {
                    ResizeGlyph(color = MaterialTheme.colorScheme.primary)
                }
                OverlayIconButton(description = "删除卡片", onClick = onRemove, danger = true) {
                    CloseGlyph(color = Color.White)
                }
            }
        }
    }
}

/** 简洁磨砂卡片底：浅色/深色主题都有一层柔和的面色与细描边。 */
@Composable
private fun FrostedBackdrop() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surface.copy(alpha = 0.80f),
                        scheme.surfaceVariant.copy(alpha = 0.52f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = scheme.onSurface.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp),
            ),
    )
}

@Composable
private fun ColumnScope.StatCardContent(
    card: StatsCard,
    slot: GridRect,
    page: StatsPageData,
) {
    val big = slot.rows >= 2
    val wide = slot.columns >= 2
    if (card.isChart) {
        ChartCardContent(card = card, big = big, wide = wide, page = page)
    } else {
        MetricCardContent(card = card, big = big, wide = wide, page = page)
    }
}

@Composable
private fun ColumnScope.ChartCardContent(
    card: StatsCard,
    big: Boolean,
    wide: Boolean,
    page: StatsPageData,
) {
    when {
        big && wide -> {
            CardTitle(card.label)
            Spacer(modifier = Modifier.height(4.dp))
            ChartBody(
                card = card,
                page = page,
                modifier = Modifier.fillMaxWidth().weight(1f),
                showLabels = true,
                // 底部 ChartFootNote 已经显示平均/最高，这里不再重复。
                showDetail = false,
            )
            ChartFootNote(card = card, page = page)
        }
        big -> {
            CardTitle(card.label)
            Spacer(modifier = Modifier.height(4.dp))
            ChartBody(
                card = card,
                page = page,
                modifier = Modifier.fillMaxWidth().weight(1f),
                showLabels = true,
                showDetail = false,
            )
            ChartFootNote(card = card, page = page)
        }
        wide -> {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChartSummary(
                    card = card,
                    page = page,
                    modifier = Modifier.widthIn(min = 70.dp, max = 108.dp),
                )
                ChartBody(
                    card = card,
                    page = page,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    showLabels = false,
                    showDetail = false,
                )
            }
        }
        else -> {
            CardTitle(card.label)
            Spacer(modifier = Modifier.height(2.dp))
            ChartBody(
                card = card,
                page = page,
                modifier = Modifier.fillMaxWidth().weight(1f),
                showLabels = false,
                showDetail = false,
            )
        }
    }
}

@Composable
private fun ChartBody(
    card: StatsCard,
    page: StatsPageData,
    modifier: Modifier,
    showLabels: Boolean,
    showDetail: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    when (card) {
        StatsCard.TREND -> TrendChart(
            points = page.trend.points,
            average = page.trend.average,
            showDetail = showDetail,
            modifier = modifier,
        )
        StatsCard.HOUR_DISTRIBUTION -> MiniBarChart(
            values = page.distribution.hour,
            color = scheme.primary,
            labels = if (showLabels) hourLabels() else emptyList(),
            labelEvery = 3,
            modifier = modifier,
        )
        StatsCard.WEEKDAY_DISTRIBUTION -> MiniBarChart(
            values = page.distribution.weekday,
            color = scheme.tertiary,
            labels = if (showLabels) weekdayLabels() else emptyList(),
            modifier = modifier,
        )
        StatsCard.BOX_PLOT -> {
            val box = page.boxPlot
            if (box == null) {
                EmptyChartBox(modifier)
            } else {
                BoxPlotMini(box = box, modifier = modifier)
            }
        }
        else -> EmptyChartBox(modifier)
    }
}

/** 宽卡左侧摘要：把图表的关键数字放在左边，图表本体放到右边。 */
@Composable
private fun ChartSummary(card: StatsCard, page: StatsPageData, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val metrics = page.metrics
    Column(modifier = modifier) {
        CardTitle(card.label)
        when (card) {
            StatsCard.TREND -> {
                Text(
                    text = "平均 ${formatOneDecimal(page.trend.average)} 次",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "最高 ${page.trend.max} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatsCard.HOUR_DISTRIBUTION -> {
                Text(
                    text = formatHour(metrics.bestHour),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "最常时段",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatsCard.WEEKDAY_DISTRIBUTION -> {
                Text(
                    text = formatWeekday(metrics.bestWeekday),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "最常星期",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            StatsCard.BOX_PLOT -> {
                val box = page.boxPlot
                Text(
                    text = box?.let { "中位 ${formatOneDecimal(it.median)}" } ?: "—",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = box?.let { "Q1 ${formatOneDecimal(it.q1)}" } ?: "样本不足",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            else -> Unit
        }
    }
}

/** 大卡底部的一行详情。 */
@Composable
private fun ChartFootNote(card: StatsCard, page: StatsPageData) {
    val metrics = page.metrics
    val text = when (card) {
        StatsCard.TREND -> "平均 ${formatOneDecimal(page.trend.average)} 次 · 最高 ${page.trend.max} 次"
        StatsCard.HOUR_DISTRIBUTION -> "最常时段 ${formatHour(metrics.bestHour)}"
        StatsCard.WEEKDAY_DISTRIBUTION -> "最常星期 ${formatWeekday(metrics.bestWeekday)}"
        StatsCard.BOX_PLOT -> page.boxPlot?.let {
            "每周 · Q1 ${formatOneDecimal(it.q1)} · 中位 ${formatOneDecimal(it.median)} · Q3 ${formatOneDecimal(it.q3)}"
        } ?: "还没有足够的数据"
        else -> ""
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColumnScope.MetricCardContent(
    card: StatsCard,
    big: Boolean,
    wide: Boolean,
    page: StatsPageData,
) {
    if (card == StatsCard.CHANGE) {
        ChangeMetricContent(big = big, wide = wide, page = page)
        return
    }

    val scheme = MaterialTheme.colorScheme
    val valueStyle = when {
        big && wide -> MaterialTheme.typography.displaySmall
        big -> MaterialTheme.typography.headlineMedium
        wide -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    val caption = metricCaption(card, page)
    val detail = metricDetail(card, page)

    when {
        big && wide -> {
            CardTitle(card.label)
            Spacer(modifier = Modifier.height(2.dp))
            MetricValue(
                card = card,
                page = page,
                style = valueStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        big -> {
            CardTitle(card.label)
            Spacer(modifier = Modifier.weight(1f))
            MetricValue(
                card = card,
                page = page,
                style = valueStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        wide -> {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CardTitle(card.label)
                    MetricValue(
                        card = card,
                        page = page,
                        style = valueStyle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
        else -> {
            CardTitle(card.label)
            MetricValue(
                card = card,
                page = page,
                style = valueStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.ChangeMetricContent(big: Boolean, wide: Boolean, page: StatsPageData) {
    val scheme = MaterialTheme.colorScheme
    val metrics = page.metrics
    CardTitle("环比")
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChangeRing(changeRatio = metrics.changeRatio, big = big)
            Column {
                Text(
                    text = "vs 上一个周期",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "上一周期 ${metrics.previousCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ChangeRing(changeRatio = metrics.changeRatio, big = big)
            Text(
                text = "上一周期 ${metrics.previousCount} 次",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MetricValue(
    card: StatsCard,
    page: StatsPageData,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val numeric = metricValueNumber(card, page)
    if (numeric != null) {
        RollingNumber(
            value = numeric,
            modifier = modifier,
            formatter = metricValueFormatter(card),
            style = style.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 1,
        )
    } else {
        Text(
            text = metricValueText(card, page),
            modifier = modifier,
            style = style.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun metricValueNumber(card: StatsCard, page: StatsPageData): Double? {
    val metrics = page.metrics
    return when (card) {
        StatsCard.PERIOD_COUNT -> metrics.count.toDouble()
        StatsCard.ACTIVE_DAYS -> metrics.activeDays.toDouble()
        StatsCard.MAX_DAY -> metrics.maxDayCount.toDouble()
        StatsCard.DAILY_AVERAGE -> metrics.dailyAverage
        StatsCard.ACTIVE_DAILY_AVERAGE -> metrics.activeDailyAverage
        StatsCard.LONGEST_STREAK -> page.longestStreak.toDouble()
        StatsCard.BEST_HOUR -> metrics.bestHour?.toDouble()
        else -> null
    }
}

private fun metricValueText(card: StatsCard, page: StatsPageData): String {
    val metrics = page.metrics
    return when (card) {
        StatsCard.AVERAGE_INTERVAL -> formatInterval(metrics.averageInterval)
        StatsCard.MEDIAN_INTERVAL -> formatInterval(metrics.medianInterval)
        StatsCard.BEST_WEEKDAY -> formatWeekday(metrics.bestWeekday)
        StatsCard.LAST_RECORD -> formatLastRecord(page.lastRecordAt, ZoneId.systemDefault())
        else -> "—"
    }
}

private fun metricValueFormatter(card: StatsCard): (Double) -> String = when (card) {
    StatsCard.PERIOD_COUNT,
    StatsCard.ACTIVE_DAYS,
    StatsCard.MAX_DAY,
    StatsCard.LONGEST_STREAK,
    -> { value -> value.toInt().toString() }
    StatsCard.BEST_HOUR -> { value -> formatHour(value.toInt()) }
    else -> { value -> formatOneDecimal(value) }
}

private fun metricCaption(card: StatsCard, page: StatsPageData): String {
    val metrics = page.metrics
    return when (card) {
        StatsCard.PERIOD_COUNT -> "共 ${metrics.daysInRange} 天"
        StatsCard.ACTIVE_DAYS -> "记录率 ${formatPercent(metrics.recordRate)}"
        StatsCard.MAX_DAY -> metrics.maxDayDate?.format(DateTimeFormatter.ofPattern("M/d")) ?: "还没有记录"
        StatsCard.DAILY_AVERAGE -> "含未记录的天"
        StatsCard.ACTIVE_DAILY_AVERAGE -> "只看有记录的天"
        StatsCard.LONGEST_STREAK -> "历史最长连续"
        StatsCard.AVERAGE_INTERVAL -> "相邻两次的均值"
        StatsCard.MEDIAN_INTERVAL -> "较少受极端值影响"
        StatsCard.BEST_HOUR -> "次数最多的小时"
        StatsCard.BEST_WEEKDAY -> "一周里最活跃的一天"
        StatsCard.LAST_RECORD -> "到最近一次记录"
        StatsCard.CHANGE -> ""
        else -> ""
    }
}

private fun metricDetail(card: StatsCard, page: StatsPageData): String {
    val metrics = page.metrics
    return when (card) {
        StatsCard.PERIOD_COUNT -> "有记录 ${metrics.activeDays} 天"
        StatsCard.ACTIVE_DAYS -> "共 ${metrics.daysInRange} 天"
        StatsCard.MAX_DAY -> metrics.maxDayDate?.format(DateTimeFormatter.ofPattern("M/d")) ?: ""
        StatsCard.DAILY_AVERAGE -> "活跃日均 ${formatOneDecimal(metrics.activeDailyAverage)}"
        StatsCard.ACTIVE_DAILY_AVERAGE -> "日均 ${formatOneDecimal(metrics.dailyAverage)}"
        StatsCard.LONGEST_STREAK -> "当前连续 ${page.currentStreak} 天"
        StatsCard.AVERAGE_INTERVAL -> "中位 ${formatInterval(metrics.medianInterval)}"
        StatsCard.MEDIAN_INTERVAL -> "平均 ${formatInterval(metrics.averageInterval)}"
        StatsCard.BEST_HOUR -> "共 ${metrics.count} 次"
        StatsCard.BEST_WEEKDAY -> "共 ${metrics.count} 次"
        StatsCard.LAST_RECORD -> "到最近一次记录"
        StatsCard.CHANGE -> "上一周期 ${metrics.previousCount} 次"
        else -> ""
    }
}

private fun hourLabels(): List<String> =
    (0 until 24).map { String.format(Locale.CHINA, "%02d", it) }

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyChartBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "还没有足够的数据",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverlayIconButton(
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background = if (danger) scheme.error else scheme.surfaceVariant.copy(alpha = 0.92f)
    // 图标动效：按下时缩小回弹，跟手一点，不再像一块死板的贴纸。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.78f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "overlay-icon-press",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { this.contentDescription = description }
            .then(
                Modifier.drawBehind {
                    if (!danger) {
                        drawCircle(
                            color = scheme.outline.copy(alpha = 0.35f),
                            radius = size.minDimension / 2f - 1f,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ResizeGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = 1.6.dp.toPx()
        val arm = size.minDimension * 0.32f
        // 左上角
        drawLine(color, Offset(0f, 0f), Offset(arm, 0f), stroke)
        drawLine(color, Offset(0f, 0f), Offset(0f, arm), stroke)
        // 右下角
        drawLine(color, Offset(size.width, size.height), Offset(size.width - arm, size.height), stroke)
        drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - arm), stroke)
    }
}

@Composable
private fun CloseGlyph(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke)
    }
}

@Composable
private fun TrendChart(
    points: List<TrendPoint>,
    average: Double,
    showDetail: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = rememberChartProgress()
    val counts = points.map { it.count }
    val maxCount = max(1, counts.maxOrNull() ?: 1)
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (counts.isEmpty()) return@Canvas
            val chartTop = 4.dp.toPx()
            val chartBottom = size.height - 2.dp.toPx()
            val chartHeight = chartBottom - chartTop
            val stepX = if (counts.size <= 1) 0f else size.width / (counts.size - 1).toFloat()

            val linePath = Path()
            val areaPath = Path()
            counts.forEachIndexed { index, count ->
                val x = stepX * index
                val y = chartBottom - chartHeight * (count.toFloat() / maxCount) * progress
                if (index == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, chartBottom)
                    areaPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            areaPath.lineTo(size.width, chartBottom)
            areaPath.close()

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    listOf(scheme.primary.copy(alpha = 0.35f), scheme.primary.copy(alpha = 0.02f)),
                ),
            )
            drawPath(
                path = linePath,
                color = scheme.primary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            if (average > 0) {
                val avgY = chartBottom - chartHeight * (average.toFloat() / maxCount).coerceIn(0f, 1f)
                drawLine(
                    color = scheme.onSurfaceVariant.copy(alpha = 0.45f),
                    start = Offset(0f, avgY),
                    end = Offset(size.width, avgY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                )
            }
        }
        if (showDetail) {
            Text(
                text = "平均 ${formatOneDecimal(average)} · 最高 ${counts.maxOrNull() ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun MiniBarChart(
    values: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    labelEvery: Int = 1,
) {
    val scheme = MaterialTheme.colorScheme
    val progress = rememberChartProgress()
    val maxValue = max(1, values.maxOrNull() ?: 1)
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val gap = 2.dp.toPx()
            val slotWidth = size.width / values.size
            val barWidth = max(1.5f, slotWidth - gap)
            values.forEachIndexed { index, value ->
                // 柱子从左到右依次长出来。
                val grow = ((progress * values.size) - index).coerceIn(0f, 1f)
                val height = (size.height - 2.dp.toPx()) * value / maxValue * grow
                drawRoundRect(
                    color = color.copy(alpha = (if (value == 0) 0.18f else 0.85f) * grow.coerceAtLeast(0.15f)),
                    topLeft = Offset(index * slotWidth + gap / 2f, size.height - height),
                    size = Size(barWidth, max(1.5f, height)),
                    cornerRadius = CornerRadius(barWidth / 3f),
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEachIndexed { index, label ->
                    if (index % labelEvery == 0) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxPlotMini(box: BoxPlotSummary, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val progress = rememberChartProgress()
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val midX = size.width / 2f
            val top = 6.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val plotHeight = bottom - top
            val pivot = Offset(midX, size.height / 2f)
            scale(scaleX = 1f, scaleY = progress.coerceAtLeast(0.001f), pivot = pivot) {
            fun yFor(value: Double): Float {
                val range = (box.max - box.min).coerceAtLeast(0.0001)
                return (bottom - plotHeight * ((value - box.min) / range)).toFloat()
            }
            val cap = 10.dp.toPx()
            val minY = yFor(box.min)
            val maxY = yFor(box.max)
            val q1Y = yFor(box.q1)
            val q3Y = yFor(box.q3)
            val medianY = yFor(box.median)

            drawLine(scheme.onSurfaceVariant, Offset(midX, maxY), Offset(midX, minY), 1.4.dp.toPx())
            drawLine(scheme.onSurfaceVariant, Offset(midX - cap, maxY), Offset(midX + cap, maxY), 1.4.dp.toPx())
            drawLine(scheme.onSurfaceVariant, Offset(midX - cap, minY), Offset(midX + cap, minY), 1.4.dp.toPx())

            val boxHalf = size.width * 0.22f
            drawRoundRect(
                color = scheme.primary.copy(alpha = 0.30f),
                topLeft = Offset(midX - boxHalf, q3Y),
                size = Size(boxHalf * 2, (q1Y - q3Y).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx()),
            )
            drawLine(scheme.primary, Offset(midX - boxHalf, medianY), Offset(midX + boxHalf, medianY), 2.4.dp.toPx())
            box.outliers.forEachIndexed { index, outlier ->
                val y = yFor(outlier)
                val x = midX + if (index % 2 == 0) boxHalf + 8.dp.toPx() else -(boxHalf + 8.dp.toPx())
                drawCircle(scheme.error, radius = 2.6.dp.toPx(), center = Offset(x, y))
            }
            }
        }
        Text(
            text = "样本 ${box.sampleSize} 周 · 中位 ${formatOneDecimal(box.median)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChangeRing(
    changeRatio: Double?,
    big: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val up = (changeRatio ?: 0.0) >= 0
    val ringColor = if (changeRatio == null) scheme.onSurfaceVariant else if (up) scheme.primary else scheme.error
    val trackColor = scheme.onSurface.copy(alpha = 0.12f)
    val targetProgress = if (changeRatio == null) 0f else (abs(changeRatio).coerceAtMost(2.0) / 2.0).toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(changeRatio) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = targetProgress,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }
    val ringSize = if (big) 72.dp else 58.dp
    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = (ringSize.value * 0.11f).dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = changeRatio?.let { String.format(Locale.CHINA, "%+.0f%%", it * 100) } ?: "—",
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                color = ringColor,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "环比",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 像闹钟选时间一样的滚轮尺寸选择器。
 */
@Composable
internal fun WheelSizePickerDialog(
    initialSize: CardSize,
    onDismiss: () -> Unit,
    onConfirm: (CardSize) -> Unit,
) {
    val sizes = CardSize.entries
    val initialIndex = sizes.indexOf(initialSize).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }
    val scheme = MaterialTheme.colorScheme

    // 滚轮停止滚动时磁吸到中间那一项，做出「闹钟选时间」的手感。
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs(item.offset + item.size / 2 - center)
            }?.index ?: 0
        }.collect { selectedIndex = it.coerceIn(0, sizes.lastIndex) }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val target = selectedIndex.coerceIn(0, sizes.lastIndex)
                if (listState.firstVisibleItemIndex != target || listState.firstVisibleItemScrollOffset != 0) {
                    listState.animateScrollToItem(target)
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "卡片大小",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(46.dp * 3),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primary.copy(alpha = 0.10f))
                            .border(
                                width = 1.dp,
                                color = scheme.primary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp),
                            ),
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 46.dp),
                    ) {
                        items(sizes.size) { index ->
                            val size = sizes[index]
                            Box(
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${size.label} · ${sizeHint(size)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (index == selectedIndex) scheme.primary else scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(scheme.surface.copy(alpha = 0.95f), Color.Transparent),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, scheme.surface.copy(alpha = 0.95f)),
                                ),
                            ),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(sizes[selectedIndex]) }) { Text("确定") }
                }
            }
        }
    }
}

private fun sizeHint(size: CardSize): String = when (size) {
    CardSize.SMALL -> "迷你"
    CardSize.WIDE -> "宽"
    CardSize.TALL -> "高"
    CardSize.LARGE -> "大"
}

@Composable
internal fun AddCardDialog(
    hiddenCards: List<StatsCard>,
    onAdd: (StatsCard) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("增加卡片") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (hiddenCards.isEmpty()) {
                    Text(
                        text = "所有卡片都已经在统计页上了",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                hiddenCards.forEach { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onAdd(card) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (card.isChart) "图表" else "指标",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(48.dp),
                        )
                        Text(
                            text = card.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "＋ 添加",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onReset) { Text("恢复默认布局") }
        },
    )
}
