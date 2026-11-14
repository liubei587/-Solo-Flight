package com.feilema.app.domain.usecase.stats

import java.time.LocalDate

/**
 * 统计时间段。图表和指标可以各自独立选择要看的范围。
 */
enum class StatsRange(val label: String, val shortLabel: String, val days: Long?) {
    WEEK(label = "近 7 天", shortLabel = "7 天", days = 7L),
    MONTH(label = "近 30 天", shortLabel = "30 天", days = 30L),
    QUARTER(label = "近 90 天", shortLabel = "90 天", days = 90L),
    ALL(label = "全部", shortLabel = "全部", days = null),
    ;

    /**
     * 该时间段的起始日期（含）。[ALL] 从第一条记录那天开始，没有记录时退回到今天。
     */
    fun windowStart(today: LocalDate, firstDate: LocalDate?): LocalDate =
        days?.let { today.minusDays(it - 1) } ?: firstDate ?: today
}
