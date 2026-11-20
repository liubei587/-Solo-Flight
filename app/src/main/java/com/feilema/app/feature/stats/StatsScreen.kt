package com.feilema.app.feature.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feilema.app.core.ui.components.RollingNumber
import com.feilema.app.domain.model.StatsCard
import com.feilema.app.domain.model.StatsLayout
import com.feilema.app.domain.model.withCardPosition
import com.feilema.app.domain.model.withDragged
import com.feilema.app.domain.usecase.stats.StatsPageData
import com.feilema.app.domain.usecase.stats.StatsRange
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
fun StatsRoute(viewModel: StatsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(
        uiState = uiState,
        onChartRangeChange = viewModel::setChartRange,
        onMetricRangeChange = viewModel::setMetricRange,
        onSaveLayout = viewModel::saveLayout,
        onResetLayout = viewModel::resetLayout,
    )
}

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onChartRangeChange: (StatsRange) -> Unit,
    onMetricRangeChange: (StatsRange) -> Unit,
    onSaveLayout: (StatsLayout) -> Unit,
    onResetLayout: () -> Unit,
) {
    val page = uiState.page
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var listTopPx by remember { mutableFloatStateOf(0f) }
    var listHeightPx by remember { mutableFloatStateOf(0f) }
    val viewportTop = with(density) { listTopPx.toDp() }
    val viewportBottom = with(density) { (listTopPx + listHeightPx).toDp() }

    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf(uiState.layout) }
    var resizeCard by remember { mutableStateOf<StatsCard?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var metricMenuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.layout) {
        if (!editing) draft = uiState.layout
    }

    fun startEditing() {
        draft = uiState.layout
        editing = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun finishEditing(save: Boolean) {
        if (save) onSaveLayout(draft)
        editing = false
    }

    BackHandler(enabled = editing) { showExitDialog = true }

    val layout = if (editing) draft else uiState.layout

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                listTopPx = coordinates.positionInWindow().y
                listHeightPx = coordinates.size.height.toFloat()
            },
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            StatsHeader(
                editing = editing,
                totalCount = page.totalCount,
                onEdit = ::startEditing,
                onDone = { finishEditing(save = true) },
            )
        }

        if (!page.hasData) {
            item { EmptyState() }
        } else {
            item { HeroSummary(page) }

            item {
                ChartRangeSelector(
                    selected = uiState.chartRange,
                    onSelect = onChartRangeChange,
                )
            }

            item {
                MetricRangeHeader(
                    selected = uiState.metricRange,
                    expanded = metricMenuOpen,
                    onExpandedChange = { metricMenuOpen = it },
                    onSelect = {
                        metricMenuOpen = false
                        onMetricRangeChange(it)
                    },
                )
            }

            item {
                StatsCardGrid(
                    layout = layout,
                    editing = editing,
                    page = page,
                    scrollState = listState,
                    viewportTop = viewportTop,
                    viewportBottom = viewportBottom,
                    chartAnimationKey = uiState.chartRange,
                    onEnterEdit = ::startEditing,
                    onMove = { card, toIndex -> draft = draft.withDragged(card, toIndex) },
                    onPlace = { card, rect -> draft = draft.withCardPosition(card, rect) },
                    onResize = { card -> resizeCard = card },
                    onRemove = { card ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        draft = draft.withRemoved(card)
                    },
                )
            }

            if (editing) {
                item {
                    AddCardButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddDialog = true
                        },
                    )
                }
                item {
                    Text(
                        "拖动卡片调整顺序 · 右上角可改大小或删除 · 点「完成」保存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    OutlinedButton(
                        onClick = ::startEditing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("编辑统计卡片")
                    }
                }
            }

            item {
                Text(
                    "统计只按事件发生时间计算，补录或修改时间会实时反映到图表。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    resizeCard?.let { card ->
        WheelSizePickerDialog(
            initialSize = layout.sizeOf(card),
            onDismiss = { resizeCard = null },
            onConfirm = { size ->
                draft = draft.withSize(card, size)
                resizeCard = null
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    if (showAddDialog) {
        AddCardDialog(
            hiddenCards = draft.hiddenCards,
            onAdd = { card -> draft = draft.withAdded(card) },
            onReset = {
                onResetLayout()
                draft = StatsLayout()
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("是否保存修改？") },
            text = { Text("卡片的顺序、大小和显示内容有改动，保存后再退出吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    finishEditing(save = true)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    finishEditing(save = false)
                }) { Text("不保存") }
            },
        )
    }
}

@Composable
private fun StatsHeader(
    editing: Boolean,
    totalCount: Int,
    onEdit: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "统计",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (editing) {
                    "拖动排序 · 右上角改大小或删除"
                } else {
                    "共 $totalCount 次记录 · 长按卡片可编辑"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (editing) {
            TextButton(onClick = onDone) {
                Text("完成", fontWeight = FontWeight.SemiBold)
            }
        } else {
            val editInteraction = remember { MutableInteractionSource() }
            val editPressed by editInteraction.collectIsPressedAsState()
            val breathe by rememberInfiniteTransition(label = "edit-icon-breath").animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1300),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "edit-icon-scale",
            )
            val pressScale by animateFloatAsState(
                targetValue = if (editPressed) 1.35f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "edit-icon-press",
            )
            FilledTonalIconButton(
                onClick = onEdit,
                interactionSource = editInteraction,
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = "编辑统计卡片",
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            val scale = if (editPressed) pressScale else breathe
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}

@Composable
private fun HeroSummary(page: StatsPageData) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(scheme.primary, scheme.secondary, scheme.tertiary)),
                shape = RoundedCornerShape(26.dp),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HeroStat("今日", page.todayCount, "次", scheme.onPrimary)
            HeroDivider(scheme.onPrimary)
            HeroStat("本周", page.weekCount, "次", scheme.onPrimary)
            HeroDivider(scheme.onPrimary)
            HeroStat("连续", page.currentStreak, "天", scheme.onPrimary)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Int, unit: String, contentColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.82f),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumber(
                value = value.toDouble(),
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun HeroDivider(contentColor: Color) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(1.dp)
            .background(contentColor.copy(alpha = 0.22f)),
    )
}

@Composable
private fun ChartRangeSelector(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "图表范围",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsRange.entries.forEach { range ->
                FilterChip(
                    selected = selected == range,
                    onClick = { onSelect(range) },
                    label = { Text(range.shortLabel) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MetricRangeHeader(
    selected: StatsRange,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (StatsRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "指标范围",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            AssistChip(
                onClick = { onExpandedChange(true) },
                label = { Text(selected.label) },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
                StatsRange.entries.forEach { range ->
                    DropdownMenuItem(
                        text = { Text(range.label) },
                        onClick = { onSelect(range) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "还没有记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "记录几次之后，这里会显示趋势、分布、箱线图和各项统计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddCardButton(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 图标动效：平时缓慢呼吸，按下去时放大回弹。
    val breath by rememberInfiniteTransition(label = "add-card-breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "add-card-plus",
    )
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 1.45f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "add-card-press",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(
                width = 1.5.dp,
                color = color.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "＋",
                style = MaterialTheme.typography.titleLarge,
                color = color,
                modifier = Modifier.graphicsLayer {
                    val scale = if (pressed) pressedScale else breath
                    scaleX = scale
                    scaleY = scale
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "增加卡片",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

// 格式化辅助（图表卡片里共用）。

internal fun formatAverage(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value)

internal fun formatPercent(value: Double): String =
    String.format(Locale.CHINA, "%.0f%%", value * 100)

internal fun formatHour(hour: Int?): String =
    hour?.let { String.format(Locale.CHINA, "%02d:00", it) } ?: "—"

internal fun formatWeekday(weekday: Int?): String =
    weekday?.let { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(it - 1) }
        ?: "—"

internal fun formatInterval(duration: Duration?): String {
    if (duration == null) return "—"
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "不到 1 分钟"
        minutes < 60 -> "$minutes 分钟"
        minutes < 60 * 24 -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分钟"
        }
        else -> {
            val days = minutes / (60 * 24)
            val restHours = (minutes % (60 * 24)) / 60
            if (restHours == 0L) "$days 天" else "$days 天 $restHours 小时"
        }
    }
}

internal fun formatLastRecord(instant: Instant?, zone: ZoneId): String {
    if (instant == null) return "—"
    val dateTime = instant.atZone(zone)
    return String.format(
        Locale.CHINA,
        "%d/%d %02d:%02d",
        dateTime.monthValue,
        dateTime.dayOfMonth,
        dateTime.hour,
        dateTime.minute,
    )
}

internal fun weekdayLabels(): List<String> = listOf("一", "二", "三", "四", "五", "六", "日")
