package com.feilema.app.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.feilema.app.MainActivity
import dagger.hilt.android.EntryPointAccessors
import java.time.Instant
import java.time.LocalDate

/**
 * 桌面小组件：显示今日次数，点「＋ 记录一次」直接落一条记录。
 * 点空白处打开 App。
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repository = entryPoint.behaviorRepository()
        val clock = entryPoint.clock()
        val today = LocalDate.now(clock)
        val start = today.atStartOfDay(clock.zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant()
        val count = repository.countBetween(start, end)

        provideContent {
            TodayWidgetContent(count = count)
        }
    }
}

@Composable
private fun TodayWidgetContent(count: Int) {
    val background = ColorProvider(Color(0xFF6750A4))
    val onBackground = ColorProvider(Color(0xFFFFFFFF))
    val buttonBackground = ColorProvider(Color(0x33FFFFFF))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "飞了吗 · 今日",
            style = TextStyle(color = onBackground, fontSize = 12.sp),
        )
        Text(
            text = "$count 次",
            style = TextStyle(
                color = onBackground,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "＋ 记录一次",
            modifier = GlanceModifier
                .background(buttonBackground)
                .padding(10.dp)
                .clickable(actionRunCallback<RecordAction>()),
            style = TextStyle(
                color = onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

/** 小组件上的「记录一次」：写入当前时间，然后刷新这个小组件。 */
class RecordAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repository = entryPoint.behaviorRepository()
        repository.record(occurredAt = Instant.now(entryPoint.clock()))
        // 桌面上可能放了多个「飞了吗」小组件，全部刷新。
        GlanceAppWidgetManager(context)
            .getGlanceIds(TodayWidget::class.java)
            .forEach { id -> TodayWidget().update(context, id) }
    }
}
