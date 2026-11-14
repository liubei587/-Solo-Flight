package com.feilema.app.domain.usecase.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 提醒时间的纯计算逻辑，固定 Clock 即可单测。 */
object ReminderSchedule {

    /**
     * 下一次提醒的触发时刻：
     * 今天的 [time] 还没到就用今天，已经到点（含正好等于）就顺延到明天。
     */
    fun nextTrigger(now: Instant, time: LocalTime, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val todayTrigger = today.atTime(time).atZone(zone).toInstant()
        return if (todayTrigger.isAfter(now)) {
            todayTrigger
        } else {
            today.plusDays(1).atTime(time).atZone(zone).toInstant()
        }
    }
}
