package com.feilema.app.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用内发生数据变更（记录/撤销/删除/改时间）后，主动刷新桌面上所有「飞了吗」小组件。
 * Glance 小组件不会自动监听数据库，所以这里显式刷新；失败不影响主流程。
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun refresh() {
        runCatching {
            val widget = TodayWidget()
            GlanceAppWidgetManager(context)
                .getGlanceIds(TodayWidget::class.java)
                .forEach { glanceId -> widget.update(context, glanceId) }
        }
    }
}
