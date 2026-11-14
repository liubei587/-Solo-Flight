package com.feilema.app.domain.usecase.stats

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class DayCount(val date: LocalDate, val count: Int)

data class BoxPlotSummary(
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double,
    val outliers: List<Double> = emptyList(),
    val sampleSize: Int = 0,
)

data class StatsSummary(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val totalCount: Int = 0,
    val dailyAverage: Double = 0.0,
    val activeDailyAverage: Double = 0.0,
    val streakDays: Int = 0,
    val averageInterval: Duration? = null,
    val medianInterval: Duration? = null,
    val hourDistribution: List<Int> = List(24) { 0 },
    val weekdayDistribution: List<Int> = List(7) { 0 },
    val last7Days: List<DayCount> = emptyList(),
    val last30Days: List<DayCount> = emptyList(),
    val dailyCountBox: BoxPlotSummary? = null,
) {
    val hasData: Boolean get() = totalCount > 0
}

enum class TrendGranularity(val label: String, val unit: String) {
    DAY(label = "每天", unit = "天"),
    WEEK(label = "每周", unit = "周"),
    MONTH(label = "每月", unit = "月"),
}

data class TrendPoint(
    val label: String,
    val start: LocalDate,
    val endExclusive: LocalDate,
    val count: Int,
)

data class TrendData(
    val points: List<TrendPoint> = emptyList(),
    val granularity: TrendGranularity = TrendGranularity.DAY,
    val average: Double = 0.0,
    val max: Int = 0,
) {
    val hasData: Boolean get() = points.any { it.count > 0 }
}

data class DistributionData(
    val hour: List<Int> = List(24) { 0 },
    val weekday: List<Int> = List(7) { 0 },
)

data class RangeMetrics(
    val range: StatsRange = StatsRange.MONTH,
    val count: Int = 0,
    val activeDays: Int = 0,
    val daysInRange: Int = 0,
    val dailyAverage: Double = 0.0,
    val activeDailyAverage: Double = 0.0,
    val averageInterval: Duration? = null,
    val medianInterval: Duration? = null,
    val maxDayCount: Int = 0,
    val maxDayDate: LocalDate? = null,
    val recordRate: Double = 0.0,
    val previousCount: Int = 0,
    val changeRatio: Double? = null,
    val bestHour: Int? = null,
    val bestWeekday: Int? = null,
)

data class StatsPageData(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val totalCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastRecordAt: Instant? = null,
    val chartRange: StatsRange = StatsRange.MONTH,
    val metricRange: StatsRange = StatsRange.MONTH,
    val trend: TrendData = TrendData(),
    val distribution: DistributionData = DistributionData(),
    val boxPlot: BoxPlotSummary? = null,
    val metrics: RangeMetrics = RangeMetrics(),
) {
    val hasData: Boolean get() = totalCount > 0
}
