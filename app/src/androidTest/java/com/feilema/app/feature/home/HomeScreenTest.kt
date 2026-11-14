package com.feilema.app.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.feilema.app.core.ui.theme.FeiLeMaTheme
import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 真机 instrumentation 测试：无状态 HomeScreen 的展示与回调。 */
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsCountAndRecentEvents() {
        composeRule.setContent {
            FeiLeMaTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        todayCount = 3,
                        recentEvents = listOf(
                            BehaviorEvent(
                                id = 1,
                                occurredAt = Instant.ofEpochMilli(1_000),
                                createdAt = Instant.ofEpochMilli(1_000),
                                note = "测试备注",
                            ),
                        ),
                    ),
                    onRecord = {},
                    onUndo = {},
                )
            }
        }

        composeRule.onNodeWithText("飞了吗").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("测试备注").assertIsDisplayed()
        composeRule.onNodeWithText("最近记录").assertIsDisplayed()
    }

    @Test
    fun recordAndUndoButtonsInvokeCallbacks() {
        var recorded = 0
        var undone = 0
        composeRule.setContent {
            FeiLeMaTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        todayCount = 2,
                        recentEvents = listOf(
                            BehaviorEvent(
                                id = 1,
                                occurredAt = Instant.ofEpochMilli(1_000),
                                createdAt = Instant.ofEpochMilli(1_000),
                            ),
                        ),
                    ),
                    onRecord = { recorded++ },
                    onUndo = { undone++ },
                )
            }
        }

        composeRule.onNodeWithText("记录一次").performClick()
        composeRule.onNodeWithText("撤销上一次").performClick()
        composeRule.runOnIdle {
            assertTrue(recorded == 1)
            assertTrue(undone == 1)
        }
    }

    @Test
    fun emptyStateShowsHint() {
        composeRule.setContent {
            FeiLeMaTheme {
                HomeScreen(
                    uiState = HomeUiState(),
                    onRecord = {},
                    onUndo = {},
                )
            }
        }

        composeRule.onNodeWithText("还没有记录，点上面的按钮开始。").assertIsDisplayed()
        composeRule.onNodeWithText("0").assertIsDisplayed()
    }
}
