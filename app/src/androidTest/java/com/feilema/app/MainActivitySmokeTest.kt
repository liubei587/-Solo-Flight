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

/**
 * 真机冒烟：用真实 Application（Hilt、Room、DataStore 全真）启动，切四个 tab。
 * 连上手机后可跑 `gradle connectedDebugAndroidTest`。
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesAndSwitchesTabs() {
        composeRule.onNodeWithText("飞了吗").assertIsDisplayed()
        composeRule.onNodeWithText("记录一次").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("历史").performClick()
        composeRule.onNodeWithText("补录").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("统计").performClick()
        composeRule.onNodeWithText("平均间隔").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("导出 CSV").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("今日").performClick()
        composeRule.onNodeWithText("记录一次").assertIsDisplayed()
    }
}
