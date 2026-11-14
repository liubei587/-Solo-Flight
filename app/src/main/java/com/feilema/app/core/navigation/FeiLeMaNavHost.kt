package com.feilema.app.core.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feilema.app.feature.history.HistoryRoute
import com.feilema.app.feature.home.HomeRoute
import com.feilema.app.feature.settings.SettingsRoute
import com.feilema.app.feature.stats.StatsRoute

private enum class TopLevelDestination(val label: String, val icon: ImageVector) {
    HOME("今日", Icons.Filled.Home),
    HISTORY("历史", Icons.AutoMirrored.Filled.List),
    STATS("统计", Icons.Filled.Star),
    SETTINGS("设置", Icons.Filled.Settings),
}

/**
 * 顶层导航：用本地状态直接切页，不再经过 NavHost 的返回栈。
 *
 * 之前的实现依赖 navigate + restoreState，首次点击时会先被状态恢复吞掉一次，
 * 需要点第二次才切换；改成状态驱动后每次点击都立即换页。
 */
@Composable
fun FeiLeMaNavHost() {
    var currentTabName by rememberSaveable { mutableStateOf(TopLevelDestination.HOME.name) }
    val currentTab = TopLevelDestination.entries
        .firstOrNull { it.name == currentTabName }
        ?: TopLevelDestination.HOME

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            FeiLeMaBottomBar(
                currentTab = currentTab,
                onSelect = { destination -> currentTabName = destination.name },
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (slideInHorizontally(tween(320)) { it / 8 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { it / 8 } + fadeOut(tween(160)))
                }
            },
            label = "tab-content",
        ) { tab ->
            when (tab) {
                TopLevelDestination.HOME -> HomeRoute()
                TopLevelDestination.HISTORY -> HistoryRoute()
                TopLevelDestination.STATS -> StatsRoute()
                TopLevelDestination.SETTINGS -> SettingsRoute()
            }
        }
    }
}

@Composable
private fun FeiLeMaBottomBar(
    currentTab: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentTab == destination
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(220),
                    label = "tab-tint",
                )
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(220),
                    label = "tab-pill",
                )
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.14f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                    label = "tab-scale",
                )
                val lift by animateDpAsState(
                    targetValue = if (selected) (-2).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "tab-lift",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onSelect(destination) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = lift)
                            .clip(RoundedCornerShape(50))
                            .background(pillColor)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = tint,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
