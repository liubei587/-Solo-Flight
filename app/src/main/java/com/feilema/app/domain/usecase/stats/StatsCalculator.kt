package com.feilema.app.domain.usecase.stats

import com.feilema.app.domain.model.BehaviorEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 所有统计口径的唯一实现。纯 Kotlin，固定传入 now/zone，方便 JVM 单测锁定口径。
 */
object StatsCalculator {

    private const val TREND_DAYS = 30L

    /** 箱线图最多回看半年，避免「全部」范围把几年前的节奏也混进来。 */
    private const val BOX_PLOT_MAX_DAYS = 180L
    private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

    fun build(events: List<BehaviorEvent>, now: Instant, zone: ZoneId): StatsSummary {
        if (events.isEmpty()) return StatsSummary()

        val today = now.atZone(zone).toLocalDate()
        val weekStartDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sorted = events.sortedBy { it.occurredAt }
        val countsByDate = countByDate(sorted, zone)
        val weekCount = countsByDate.entries
            .filter { !it.key.isBefore(weekStartDate) && !it.key.isAfter(today) }
            .sumOf { it.value }
        val firstDate = countsByDate.keys.minOrNull() ?: today
        val daysSpan = (ChronoUnit.DAYS.between(firstDate, today) + 1).toInt().coerceAtLeast(1)
        val intervals = intervalMillis(sorted)

        return StatsSummary(
            todayCount = countsByDate[today] ?: 0,
            weekCount = weekCount,
            totalCount = sorted.size,
            dailyAverage = sorted.size.toDouble() / daysSpan,
            activeDailyAverage = sorted.size.toDouble() / countsByDate.size.coerceAtLeast(1),
            streakDays = currentStreak(countsByDate.keys, today),
            averageInterval = meanDuration(intervals),
            medianInterval = medianDuration(intervals),
            hourDistribution = hourDistribution(sorted, zone),
            weekdayDistribution = weekdayDistribution(sorted, zone),
            last7Days = dayCounts(countsByDate, today, 7L),
            last30Days = dayCounts(countsByDate, today, TREND_DAYS),
            dailyCountBox = buildBoxPlot(
                countsByDate.entries
                    .filter { !it.key.isBefore(today.minusDays(TREND_DAYS - 1)) }
                    .map { it.value.toDouble() }
            ),
        )
    }

    /**
     * 一次算出统计页需要的全部数据：图表按 [chartRange] 聚合，指标卡按 [metricRange] 计算，
     * 两者互相独立，界面上可以分别切换。
     */
    fun buildPage(
        events: List<BehaviorEvent>,
        now: Instant,
        zone: ZoneId,
        chartRange: StatsRange,
        metricRange: StatsRange,
    ): StatsPageData {
        val summary = build(events, now, zone)
        if (events.isEmpty()) {
            return StatsPageData(chartRange = chartRange, metricRange = metricRange)
        }

        val today = now.atZone(zone).toLocalDate()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant()
        val countsByDate = countByDate(events, zone)
        val firstDate = countsByDate.keys.minOrNull() ?: today
        val chartWindowStart = chartRange.windowStart(today, firstDate)
        val chartCounts = countsByDate.filterKeys { !it.isBefore(chartWindowStart) && !it.isAfter(today) }
        val chartEvents = events.filter {
            val date = it.occurredAt.atZone(zone).toLocalDate()
            !date.isBefore(chartWindowStart) && !date.isAfter(today)
        }

        return StatsPageData(
            todayCount = summary.todayCount,
            weekCount = summary.weekCount,
            totalCount = summary.totalCount,
            currentStreak = summary.streakDays,
            longestStreak = longestStreak(countsByDate.keys),
            lastRecordAt = events.maxOfOrNull { it.occurredAt },
            chartRange = chartRange,
            metricRange = metricRange,
            trend = buildTrend(chartCounts, chartWindowStart, today, chartRange),
            distribution = DistributionData(
                hour = hourDistribution(chartEvents, zone),
                weekday = weekdayDistribution(chartEvents, zone),
            ),
            // 箱线图独立于图表范围：固定回看最近半年（数据不足半年则从首条记录起），
            // 每个点是一周的总次数，避免切换 7 天 / 30 天时样本只剩几周。
            boxPlot = buildBoxPlot(
                weeklyCounts(
                    countsByDate = countsByDate,
                    windowStart = maxOf(firstDate, today.minusDays(BOX_PLOT_MAX_DAYS - 1)),
                    today = today,
                ),
            ),
            metrics = buildRangeMetrics(events, zone, metricRange, today, tomorrowStart, firstDate),
        )
    }

    fun currentStreak(recordedDates: Set<LocalDate>, today: LocalDate): Int {
        if (recordedDates.isEmpty()) return 0
        val start = when {
            recordedDates.contains(today) -> today
            recordedDates.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var cursor = start
        while (recordedDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun longestStreak(recordedDates: Set<LocalDate>): Int {
        if (recordedDates.isEmpty()) return 0
        val sorted = recordedDates.sorted()
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index - 1].plusDays(1) == sorted[index]) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }

    fun buildBoxPlot(values: List<Double>): BoxPlotSummary? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val median = medianOf(sorted)
        if (sorted.size < 4) {
            return BoxPlotSummary(
                min = sorted.first(),
                q1 = median,
                median = median,
                q3 = median,
                max = sorted.last(),
                sampleSize = sorted.size,
            )
        }
        val middle = sorted.size / 2
        val lowerHalf = sorted.subList(0, middle)
        val upperHalf = if (sorted.size % 2 == 0) sorted.subList(middle, sorted.size) else sorted.subList(middle + 1, sorted.size)
        val q1 = medianOf(lowerHalf)
        val q3 = medianOf(upperHalf)
        val iqr = q3 - q1
        val lowFence = q1 - 1.5 * iqr
        val highFence = q3 + 1.5 * iqr
        val inliers = sorted.filter { it >= lowFence && it <= highFence }
        val outliers = sorted.filter { it < lowFence || it > highFence }
        return BoxPlotSummary(
            min = inliers.firstOrNull() ?: sorted.first(),
            q1 = q1,
            median = median,
            q3 = q3,
            max = inliers.lastOrNull() ?: sorted.last(),
            outliers = outliers,
            sampleSize = sorted.size,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 时间段聚合
    // ---------------------------------------------------------------------------------------------

    private fun buildTrend(
        countsByDate: Map<LocalDate, Int>,
        windowStart: LocalDate,
        today: LocalDate,
        range: StatsRange,
    ): TrendData {
        val spanDays = (ChronoUnit.DAYS.between(windowStart, today) + 1).toInt().coerceAtLeast(1)
        val granularity = granularityFor(range, spanDays)
        val points = when (granularity) {
            TrendGranularity.DAY -> dailyPoints(countsByDate, windowStart, today)
            TrendGranularity.WEEK -> weeklyPoints(countsByDate, windowStart, today)
            TrendGranularity.MONTH -> monthlyPoints(countsByDate, windowStart, today)
        }
        val total = points.sumOf { it.count }
        return TrendData(
            points = points,
            granularity = granularity,
            average = if (points.isEmpty()) 0.0 else total.toDouble() / points.size,
            max = points.maxOfOrNull { it.count } ?: 0,
        )
    }

    private fun granularityFor(range: StatsRange, spanDays: Int): TrendGranularity = when (range) {
        StatsRange.QUARTER -> TrendGranularity.WEEK
        StatsRange.ALL -> when {
            spanDays <= 31 -> TrendGranularity.DAY
            spanDays <= 120 -> TrendGranularity.WEEK
            else -> TrendGranularity.MONTH
        }
        else -> TrendGranularity.DAY
    }

    private fun dailyPoints(countsByDate: Map<LocalDate, Int>, start: LocalDate, today: LocalDate): List<TrendPoint> {
        val days = (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
        return (0 until days).map { offset ->
            val date = start.plusDays(offset.toLong())
            TrendPoint(
                label = DAY_LABEL.format(date),
                start = date,
                endExclusive = date.plusDays(1),
                count = countsByDate[date] ?: 0,
            )
        }
    }

    private fun weeklyPoints(countsByDate: Map<LocalDate, Int>, windowStart: LocalDate, today: LocalDate): List<TrendPoint> {
        val points = mutableListOf<TrendPoint>()
        var weekStart = windowStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        while (!weekStart.isAfter(today)) {
            val start = if (weekStart.isBefore(windowStart)) windowStart else weekStart
            val weekEnd = weekStart.plusDays(7)
            val end = if (weekEnd.isAfter(today.plusDays(1))) today.plusDays(1) else weekEnd
            points += TrendPoint(
                label = DAY_LABEL.format(start),
                start = start,
                endExclusive = end,
                count = sumCounts(countsByDate, start, end),
            )
            weekStart = weekStart.plusDays(7)
        }
        return points
    }

    private fun monthlyPoints(countsByDate: Map<LocalDate, Int>, windowStart: LocalDate, today: LocalDate): List<TrendPoint> {
        val points = mutableListOf<TrendPoint>()
        var monthStart = windowStart.withDayOfMonth(1)
        while (!monthStart.isAfter(today)) {
            val start = if (monthStart.isBefore(windowStart)) windowStart else monthStart
            val nextMonth = monthStart.plusMonths(1)
            val end = if (nextMonth.isAfter(today.plusDays(1))) today.plusDays(1) else nextMonth
            points += TrendPoint(
                label = "${start.monthValue}月",
                start = start,
                endExclusive = end,
                count = sumCounts(countsByDate, start, end),
            )
            monthStart = nextMonth
        }
        return points
    }

    private fun buildRangeMetrics(
        events: List<BehaviorEvent>,
        zone: ZoneId,
        range: StatsRange,
        today: LocalDate,
        tomorrowStart: Instant,
        firstDate: LocalDate,
    ): RangeMetrics {
        val windowStart = range.windowStart(today, firstDate)
        val startInstant = windowStart.atStartOfDay(zone).toInstant()
        val periodEvents = events
            .filter { !it.occurredAt.isBefore(startInstant) && it.occurredAt.isBefore(tomorrowStart) }
            .sortedBy { it.occurredAt }
        val counts = countByDate(periodEvents, zone)
        val activeDays = counts.size
        val daysInRange = (ChronoUnit.DAYS.between(windowStart, today) + 1).toInt().coerceAtLeast(1)
        val intervals = intervalMillis(periodEvents)
        val maxEntry = counts.maxByOrNull { it.value }
        val previousCount = if (range.days == null) {
            0
        } else {
            val previousStart = windowStart.minusDays(range.days).atStartOfDay(zone).toInstant()
            events.count { !it.occurredAt.isBefore(previousStart) && it.occurredAt.isBefore(startInstant) }
        }
        val changeRatio = if (range.days != null && previousCount > 0) {
            (periodEvents.size - previousCount).toDouble() / previousCount
        } else {
            null
        }
        val hours = hourDistribution(periodEvents, zone)
        val weekdays = weekdayDistribution(periodEvents, zone)
        return RangeMetrics(
            range = range,
            count = periodEvents.size,
            activeDays = activeDays,
            daysInRange = daysInRange,
            dailyAverage = periodEvents.size.toDouble() / daysInRange,
            activeDailyAverage = if (activeDays > 0) periodEvents.size.toDouble() / activeDays else 0.0,
            averageInterval = meanDuration(intervals),
            medianInterval = medianDuration(intervals),
            maxDayCount = maxEntry?.value ?: 0,
            maxDayDate = maxEntry?.key,
            recordRate = activeDays.toDouble() / daysInRange,
            previousCount = previousCount,
            changeRatio = changeRatio,
            bestHour = hours.indexOfMaxOrNull(),
            bestWeekday = weekdays.indexOfMaxOrNull()?.plus(1),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 基础工具
    // ---------------------------------------------------------------------------------------------

    private fun countByDate(events: List<BehaviorEvent>, zone: ZoneId): Map<LocalDate, Int> =
        events.groupingBy { it.occurredAt.atZone(zone).toLocalDate() }.eachCount()

    private fun dayCounts(countsByDate: Map<LocalDate, Int>, today: LocalDate, days: Long): List<DayCount> =
        (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset)
            DayCount(date = date, count = countsByDate[date] ?: 0)
        }

    /**
     * 箱线图的数据点：窗口内每周的总次数（周一为一周起点）。
     *
     * 按天统计时，同一天记录多次会把箱线图拉出很长的须和离群点；按周汇总后
     * 每个点代表“这一周一共记录了几次”，更贴近实际使用节奏。
     * 窗口覆盖到的周都计入（没有记录的周记 0），窗口两端的周只统计落在窗口内的天数。
     */
    private fun weeklyCounts(
        countsByDate: Map<LocalDate, Int>,
        windowStart: LocalDate,
        today: LocalDate,
    ): List<Double> {
        val firstMonday = windowStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val totals = LinkedHashMap<LocalDate, Int>()
        var cursor = firstMonday
        while (!cursor.isAfter(lastMonday)) {
            totals[cursor] = 0
            cursor = cursor.plusWeeks(1)
        }
        for ((date, count) in countsByDate) {
            if (date.isBefore(windowStart) || date.isAfter(today)) continue
            val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            totals[weekStart] = (totals[weekStart] ?: 0) + count
        }
        return totals.values.map { it.toDouble() }
    }

    private fun intervalMillis(sorted: List<BehaviorEvent>): List<Long> {
        if (sorted.size < 2) return emptyList()
        return sorted.zipWithNext { previous, next ->
            Duration.between(previous.occurredAt, next.occurredAt).toMillis()
        }
    }

    private fun meanDuration(intervals: List<Long>): Duration? =
        if (intervals.isEmpty()) null else Duration.ofMillis(intervals.average().roundToLong())

    private fun medianDuration(intervals: List<Long>): Duration? {
        if (intervals.isEmpty()) return null
        val sorted = intervals.sorted()
        val middle = sorted.size / 2
        val millis = if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
        return Duration.ofMillis(millis)
    }

    private fun hourDistribution(events: List<BehaviorEvent>, zone: ZoneId): List<Int> {
        val counts = MutableList(24) { 0 }
        events.forEach { counts[it.occurredAt.atZone(zone).hour]++ }
        return counts
    }

    private fun weekdayDistribution(events: List<BehaviorEvent>, zone: ZoneId): List<Int> {
        val counts = MutableList(7) { 0 }
        events.forEach { counts[it.occurredAt.atZone(zone).dayOfWeek.value - 1]++ }
        return counts
    }

    private fun sumCounts(countsByDate: Map<LocalDate, Int>, start: LocalDate, endExclusive: LocalDate): Int {
        var total = 0
        var date = start
        while (date.isBefore(endExclusive)) {
            total += countsByDate[date] ?: 0
            date = date.plusDays(1)
        }
        return total
    }

    private fun List<Int>.indexOfMaxOrNull(): Int? {
        val maximum = maxOrNull() ?: return null
        return if (maximum <= 0) null else indexOf(maximum)
    }

    private fun medianOf(sorted: List<Double>): Double {
        if (sorted.isEmpty()) return 0.0
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }
}
