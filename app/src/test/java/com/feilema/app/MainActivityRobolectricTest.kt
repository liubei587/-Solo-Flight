package com.feilema.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * JVM 冒烟：Robolectric 里启动真实 MainActivity —— 真实 Hilt Application、
 * 真实 Room 数据库、真实 Compose 导航，验证“装上不会一开就崩”。
 * 这是无真机阶段的代理验证；真机安装冒烟仍以 tools/smoke.ps1 为准。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesHomeAndSwitchesTabs() {
        composeRule.onNodeWithText("飞了吗").assertIsDisplayed()
        composeRule.onNodeWithText("记录一次").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("历史", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("补录").assertExists()

        composeRule.onNodeWithContentDescription("统计", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("还没有记录").assertExists()

        composeRule.onNodeWithContentDescription("设置", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("导出 CSV").assertExists()

        composeRule.onNodeWithContentDescription("今日", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("记录一次").assertIsDisplayed()
    }
}
