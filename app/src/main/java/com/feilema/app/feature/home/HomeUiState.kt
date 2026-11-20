package com.feilema.app.feature.home

import com.feilema.app.domain.model.BehaviorEvent
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val recentEvents: List<BehaviorEvent> = emptyList(),
    /** 首页日历当前展示的月份。 */
    val calendarMonth: YearMonth = YearMonth.now(),
    /** 当前日历月份里每天的次数（只包含有记录的日子）。 */
    val monthCounts: Map<LocalDate, Int> = emptyMap(),
) {
    val canUndo: Boolean
        get() = recentEvents.isNotEmpty()
}
