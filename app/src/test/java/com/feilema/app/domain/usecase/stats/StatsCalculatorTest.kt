package com.feilema.app.domain.usecase.stats

import com.feilema.app.domain.model.BehaviorEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 用固定 Clock 锁住统计口径：所有时间都显式换算成本地时区判断。
 * 本地时区 Asia/Shanghai，now = 2026-11-13 20:00（周五）。
 */
class StatsCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Instant = Instant.parse("2026-11-13T12:00:00Z")

    private var nextId = 1L

    private fun event(occurredAtIso: String): BehaviorEvent {
        val at = Instant.parse(occurredAtIso)
        return BehaviorEvent(id = nextId++, occurredAt = at, createdAt = at)
    }

    @Test
    fun `empty events produce an empty summary`() {
        val summary = StatsCalculator.build(emptyList(), now, zone)

        assertEquals(0, summary.totalCount)
        assertEquals(0, summary.todayCount)
        assertEquals(0, summary.weekCount)
        assertEquals(0, summary.streakDays)
        assertNull(summary.averageInterval)
        assertNull(summary.medianInterval)
        assertEquals(24, summary.hourDistribution.size)
        assertEquals(7, summary.weekdayDistribution.size)
        assertEquals(false, summary.hasData)
    }

    @Test
    fun `known dataset computes every metric`() {
        val events = listOf(
            event("2026-10-01T04:00:00Z"), // 10-01 12:00 周四
            event("2026-11-08T04:00:00Z"), // 11-08 12:00 周日（上一周）
            event("2026-11-11T04:00:00Z"), // 11-11 12:00 周三
            event("2026-11-12T04:00:00Z"), // 11-12 12:00 周四
            event("2026-11-13T01:00:00Z"), // 11-13 09:00 周五
            event("2026-11-13T04:00:00Z"), // 11-13 12:00 周五
        )

        val summary = StatsCalculator.build(events, now, zone)

        assertEquals(6, summary.totalCount)
        assertEquals(2, summary.todayCount)
        // 本周从 11-09（周一）算起，10-01 和 11-08 不算
        assertEquals(4, summary.weekCount)
        // 有记录的天数：10-01、11-08、11-11、11-12、11-13
        assertEquals(1.2, summary.activeDailyAverage, 0.0001)
        // 首条记录 10-01 到今天 11-13 共 44 天
        assertEquals(6.0 / 44.0, summary.dailyAverage, 0.0001)
        // 从今天往回：11-13、11-12、11-11，11-10 缺失
        assertEquals(3, summary.streakDays)

        // 相邻间隔：38 天、3 天、1 天、21 小时、3 小时
        assertEquals(743_040_000L, summary.averageInterval?.toMillis())
        assertEquals(86_400_000L, summary.medianInterval?.toMillis())

        assertEquals(5, summary.hourDistribution[12])
        assertEquals(1, summary.hourDistribution[9])
        // 周一…周日
        assertEquals(listOf(0, 0, 1, 2, 2, 0, 1), summary.weekdayDistribution)

        assertEquals(7, summary.last7Days.size)
        assertEquals(listOf(0, 1, 0, 0, 1, 1, 2), summary.last7Days.map { it.count })
        assertEquals(LocalDate.of(2026, 11, 13), summary.last7Days.last().date)

        // 近 30 天：10-15 起，共 4 个有记录日（11-08、11-11、11-12、11-13）
        assertEquals(30, summary.last30Days.size)
        assertEquals(5, summary.last30Days.sumOf { it.count })
        val box = summary.dailyCountBox
        requireNotNull(box)
        assertEquals(4, box.sampleSize)
        assertEquals(1.0, box.min, 0.0001)
        assertEquals(1.0, box.q1, 0.0001)
        assertEquals(1.0, box.median, 0.0001)
        assertEquals(1.5, box.q3, 0.0001)
        assertEquals(2.0, box.max, 0.0001)
        assertEquals(emptyList<Double>(), box.outliers)
    }

    @Test
    fun `timezone day boundary uses local date`() {
        // UTC 11-12 16:30 = 本地 11-13 00:30，应算进今天
        val lateNight = event("2026-11-12T16:30:00Z")

        val summary = StatsCalculator.build(listOf(lateNight), now, zone)

        assertEquals(1, summary.todayCount)
        assertEquals(1, summary.last7Days.last().count)
    }

    @Test
    fun `streak allows one day grace when today is not recorded`() {
        val recorded = setOf(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11))
        assertEquals(2, StatsCalculator.currentStreak(recorded, LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun `streak is zero when neither today nor yesterday is recorded`() {
        val recorded = setOf(LocalDate.of(2026, 11, 10))
        assertEquals(0, StatsCalculator.currentStreak(recorded, LocalDate.of(2026, 11, 13)))
    }

    @Test
    fun `single event has no interval`() {
        val summary = StatsCalculator.build(listOf(event("2026-11-13T04:00:00Z")), now, zone)
        assertNull(summary.averageInterval)
        assertNull(summary.medianInterval)
        assertEquals(1, summary.totalCount)
    }

    @Test
    fun `box plot marks far away day as outlier`() {
        // 8 天各 1 次，另有 1 天 10 次：10 次应被判为离群
        val box = StatsCalculator.buildBoxPlot(
            listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0),
        )
        requireNotNull(box)
        assertEquals(9, box.sampleSize)
        assertEquals(1.0, box.median, 0.0001)
        assertEquals(1.0, box.q1, 0.0001)
        assertEquals(1.0, box.q3, 0.0001)
        assertEquals(1.0, box.min, 0.0001)
        assertEquals(1.0, box.max, 0.0001)
        assertEquals(listOf(10.0), box.outliers)
    }

    @Test
    fun `box plot with small sample keeps min and max`() {
        val box = StatsCalculator.buildBoxPlot(listOf(1.0, 2.0))
        requireNotNull(box)
        assertEquals(2, box.sampleSize)
        assertEquals(1.0, box.min, 0.0001)
        assertEquals(2.0, box.max, 0.0001)
        assertEquals(emptyList<Double>(), box.outliers)
        assertNull(StatsCalculator.buildBoxPlot(emptyList()))
    }

    @Test
    fun `page uses the selected chart window and independent metric range`() {
        val events = listOf(
            event("2026-10-01T04:00:00Z"), // 10-01 12:00 周四
            event("2026-11-08T04:00:00Z"), // 11-08 12:00 周日
            event("2026-11-11T04:00:00Z"), // 11-11 12:00 周三
            event("2026-11-12T04:00:00Z"), // 11-12 12:00 周四
            event("2026-11-13T01:00:00Z"), // 11-13 09:00 周五
            event("2026-11-13T04:00:00Z"), // 11-13 12:00 周五
        )

        val page = StatsCalculator.buildPage(events, now, zone, StatsRange.MONTH, StatsRange.MONTH)

        assertEquals(true, page.hasData)
        assertEquals(6, page.totalCount)
        assertEquals(3, page.currentStreak)
        assertEquals(3, page.longestStreak)

        // 近 30 天：10-15 起，每天一个点
        assertEquals(TrendGranularity.DAY, page.trend.granularity)
        assertEquals(30, page.trend.points.size)
        assertEquals(5, page.trend.points.sumOf { it.count })
        assertEquals(2, page.trend.max)

        // 指标口径同为近 30 天
        assertEquals(5, page.metrics.count)
        assertEquals(4, page.metrics.activeDays)
        assertEquals(30, page.metrics.daysInRange)
        assertEquals(5.0 / 30.0, page.metrics.dailyAverage, 0.0001)
        assertEquals(5.0 / 4.0, page.metrics.activeDailyAverage, 0.0001)
        assertEquals(4.0 / 30.0, page.metrics.recordRate, 0.0001)
        assertEquals(2, page.metrics.maxDayCount)
        assertEquals(LocalDate.of(2026, 11, 13), page.metrics.maxDayDate)
        assertEquals(12, page.metrics.bestHour)
        assertEquals(5, page.metrics.bestWeekday)
        // 上一个 30 天窗口（09-15~10-15）里只有 10-01 一次
        assertEquals(1, page.metrics.previousCount)
        assertEquals(4.0, page.metrics.changeRatio!!, 0.0001)

        // 箱线图按“每周次数”统计：窗口覆盖 5 个自然周，值为 0,0,0,1,4
        val box = page.boxPlot
        requireNotNull(box)
        assertEquals(5, box.sampleSize)
        assertEquals(0.0, box.min, 0.0001)
        assertEquals(0.0, box.q1, 0.0001)
        assertEquals(0.0, box.median, 0.0001)
        assertEquals(2.5, box.q3, 0.0001)
        assertEquals(4.0, box.max, 0.0001)
        assertEquals(emptyList<Double>(), box.outliers)
    }

    @Test
    fun `quarter and all ranges switch trend granularity`() {
        val events = listOf(
            event("2026-10-01T04:00:00Z"),
            event("2026-11-08T04:00:00Z"),
            event("2026-11-11T04:00:00Z"),
            event("2026-11-12T04:00:00Z"),
            event("2026-11-13T01:00:00Z"),
            event("2026-11-13T04:00:00Z"),
        )

        val quarter = StatsCalculator.buildPage(events, now, zone, StatsRange.QUARTER, StatsRange.QUARTER)
        assertEquals(TrendGranularity.WEEK, quarter.trend.granularity)
        assertEquals(14, quarter.trend.points.size)
        assertEquals(4, quarter.trend.max)
        assertEquals(6, quarter.metrics.count)
        assertEquals(90, quarter.metrics.daysInRange)

        val all = StatsCalculator.buildPage(events, now, zone, StatsRange.ALL, StatsRange.ALL)
        assertEquals(TrendGranularity.WEEK, all.trend.granularity)
        assertEquals(7, all.trend.points.size)
        assertEquals(44, all.metrics.daysInRange)
        assertEquals(0, all.metrics.previousCount)
        assertNull(all.metrics.changeRatio)
    }

    @Test
    fun `week range metrics compare with previous week`() {
        val events = listOf(
            event("2026-11-06T04:00:00Z"), // 11-06 周五，上一个 7 天窗口
            event("2026-11-08T04:00:00Z"), // 11-08 周日，本窗口
            event("2026-11-11T04:00:00Z"), // 11-11 周三
            event("2026-11-13T04:00:00Z"), // 11-13 周五
        )

        val page = StatsCalculator.buildPage(events, now, zone, StatsRange.WEEK, StatsRange.WEEK)

        assertEquals(TrendGranularity.DAY, page.trend.granularity)
        assertEquals(7, page.trend.points.size)
        assertEquals(3, page.metrics.count)
        assertEquals(3, page.metrics.activeDays)
        assertEquals(7, page.metrics.daysInRange)
        assertEquals(1, page.metrics.previousCount)
        assertEquals(2.0, page.metrics.changeRatio!!, 0.0001)
        assertEquals(3.0 / 7.0, page.metrics.recordRate, 0.0001)
        // 11-08 → 11-11 3 天，11-11 → 11-13 2 天，平均 2.5 天
        assertEquals(Duration.ofHours(60), page.metrics.averageInterval)
        // 近 7 天窗口覆盖两个自然周：11-02 那周 1 次，11-09 那周 2 次
        val box = page.boxPlot
        requireNotNull(box)
        assertEquals(2, box.sampleSize)
        assertEquals(1.0, box.min, 0.0001)
        assertEquals(2.0, box.max, 0.0001)
        assertEquals(1.5, box.median, 0.0001)
    }

    @Test
    fun `empty page is empty but keeps selected ranges`() {
        val page = StatsCalculator.buildPage(emptyList(), now, zone, StatsRange.QUARTER, StatsRange.WEEK)

        assertEquals(false, page.hasData)
        assertEquals(StatsRange.QUARTER, page.chartRange)
        assertEquals(StatsRange.WEEK, page.metricRange)
        assertEquals(0, page.trend.points.size)
        assertEquals(0, page.metrics.count)
        assertNull(page.boxPlot)
        assertNull(page.lastRecordAt)
    }
}
