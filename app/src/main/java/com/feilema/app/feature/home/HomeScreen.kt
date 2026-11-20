package com.feilema.app.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feilema.app.core.ui.components.RollingNumber
import com.feilema.app.domain.model.BehaviorEvent
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onRecord = viewModel::record,
        onUndo = viewModel::undoLast,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRecord: () -> Unit,
    onUndo: () -> Unit,
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val todayLabel = remember { LocalDate.now().format(DateLabelFormatter) }
    val currentMonth = remember { YearMonth.now() }
    val canGoNext = uiState.calendarMonth < currentMonth

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Column {
            Text(
                text = "飞了吗",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = todayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        WeekHeroCard(weekCount = uiState.weekCount, todayCount = uiState.todayCount)

        Spacer(Modifier.height(16.dp))
        MonthCalendarCard(
            month = uiState.calendarMonth,
            counts = uiState.monthCounts,
            canGoNext = canGoNext,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )

        Spacer(Modifier.height(20.dp))
        RecordButton(onRecord = onRecord)

        val undoAlpha by animateFloatAsState(
            targetValue = if (uiState.canUndo) 1f else 0.4f,
            animationSpec = tween(200),
            label = "undo-alpha",
        )
        TextButton(
            onClick = onUndo,
            enabled = uiState.canUndo,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .alpha(undoAlpha),
        ) {
            Text(text = "撤销上一次")
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近记录",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (uiState.recentEvents.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "最近 ${uiState.recentEvents.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (uiState.recentEvents.isEmpty()) {
            EmptyRecentCard()
        } else {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                uiState.recentEvents.forEachIndexed { index, event ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(240)) + expandVertically(tween(240)),
                        exit = fadeOut(tween(160)) + shrinkVertically(tween(160)),
                    ) {
                        RecentEventRow(event = event, index = index)
                    }
                    if (index != uiState.recentEvents.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "数据只保存在本机，不联网",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WeekHeroCard(weekCount: Int, todayCount: Int) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = scheme.onPrimary
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(204.dp)
            .shadow(18.dp, shape, clip = false)
            .clip(shape),
    ) {
        // 主题色渐变底，保持干净通透的卡片质感
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            scheme.primary,
                            scheme.secondary,
                            scheme.tertiary,
                        ),
                    ),
                ),
        )
        // 左上柔和空气光
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(150f, 40f),
                        radius = 640f,
                    ),
                ),
        )
        // 玻璃边缘高光
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.30f), shape),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本周已飞",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(contentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "今天 $todayCount 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.92f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(
                    value = weekCount.toDouble(),
                    style = MaterialTheme.typography.displayLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    formatter = { it.toInt().toString() },
                )
                Text(
                    text = "次",
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
            }
            Text(
                text = "周一到今天 · 点一下按钮，就多一次飞行",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.76f),
            )
        }
    }
}

@Composable
private fun RecordButton(onRecord: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "record-scale",
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (pressed) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "record-icon-rotation",
    )
    Button(
        onClick = onRecord,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(22.dp),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
        )
        Spacer(Modifier.width(8.dp))
        Text(text = "记录一次", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * 首页日历：把当前月份铺成 6×7 的格子，飞过的日子用主题色圈出来。
 * 圈的颜色跟随主题色（primary），未记录的日子保持普通文字颜色。
 */
@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    counts: Map<LocalDate, Int>,
    canGoNext: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "飞过的日子会被圈出来",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上个月",
                        tint = scheme.primary,
                    )
                }
                IconButton(onClick = onNextMonth, enabled = canGoNext) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下个月",
                        tint = if (canGoNext) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                WeekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(220)) { it / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { -it / 5 } + fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(tween(220)) { -it / 5 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(180)) { it / 5 } + fadeOut(tween(180)))
                    }
                },
                label = "calendar-month",
            ) { targetMonth ->
                val firstDay = targetMonth.atDay(1)
                val leading = firstDay.dayOfWeek.value - 1
                val daysInMonth = targetMonth.lengthOfMonth()
                Column(modifier = Modifier.fillMaxWidth()) {
                    repeat(6) { rowIndex ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            repeat(7) { columnIndex ->
                                val dayIndex = rowIndex * 7 + columnIndex
                                val dayNumber = dayIndex - leading + 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (dayNumber in 1..daysInMonth) {
                                        CalendarDay(
                                            date = targetMonth.atDay(dayNumber),
                                            count = counts[targetMonth.atDay(dayNumber)] ?: 0,
                                            today = today,
                                            dayNumber = dayNumber,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    count: Int,
    today: LocalDate,
    dayNumber: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val hasRecord = count > 0
    val isFuture = date.isAfter(today)

    // 有记录的日子做一个轻轻的入场：按日期错峰长大，避免整页同时弹出。
    var revealed by remember(date) { mutableStateOf(!hasRecord) }
    LaunchedEffect(date) {
        if (hasRecord && !revealed) {
            delay((dayNumber % 7) * 18L + 30L)
            revealed = true
        }
    }
    val appear by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.2f,
        animationSpec = tween(240),
        label = "calendar-day-$date",
    )

    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = if (hasRecord) 0.82f + 0.18f * appear else 1f
                    scaleY = if (hasRecord) 0.82f + 0.18f * appear else 1f
                }
                .then(
                    if (hasRecord) {
                        Modifier
                            .clip(CircleShape)
                            .background(scheme.primary.copy(alpha = 0.10f))
                            .border(1.5.dp, scheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (hasRecord) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    hasRecord -> scheme.primary
                    isFuture -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
                    else -> scheme.onSurface
                },
            )
        }
        if (hasRecord) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        scaleX = appear
                        scaleY = appear
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(scheme.primary)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = scheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecentCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "还没有记录，点上面的按钮开始。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentEventRow(event: BehaviorEvent, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.occurredAt.atZone(ZoneId.systemDefault()).toLocalTime()
                    .format(TimeFormatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (event.note != null) {
                Text(
                    text = event.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (index == 0) "最近" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
