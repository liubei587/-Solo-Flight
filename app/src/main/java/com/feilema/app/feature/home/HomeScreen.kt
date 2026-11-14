package com.feilema.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feilema.app.core.ui.components.RollingNumber
import com.feilema.app.core.ui.components.premiumBlur
import com.feilema.app.domain.model.BehaviorEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateLabelFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onRecord = viewModel::record,
        onUndo = viewModel::undoLast,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRecord: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayLabel = remember { LocalDate.now().format(DateLabelFormatter) }

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
            Text(text = "最近记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
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
