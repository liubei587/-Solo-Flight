package com.feilema.app.domain.model

import java.time.LocalTime

/** 每日提醒设置：开关 + 提醒时刻（本地时间，精确到分钟）。 */
data class ReminderSettings(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime.of(20, 0),
)
