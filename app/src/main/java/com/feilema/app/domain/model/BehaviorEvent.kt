package com.feilema.app.domain.model

import java.time.Instant

/**
 * 一次行为事件。
 * @param occurredAt 行为真实发生的时间（允许补录）
 * @param createdAt 写入数据库的时间
 */
data class BehaviorEvent(
    val id: Long,
    val occurredAt: Instant,
    val createdAt: Instant,
    val note: String? = null,
)
