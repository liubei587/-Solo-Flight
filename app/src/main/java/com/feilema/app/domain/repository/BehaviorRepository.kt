package com.feilema.app.domain.repository

import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface BehaviorRepository {

    fun observeRecent(limit: Int): Flow<List<BehaviorEvent>>

    /** 全部事件（新→旧），时间线与统计共用。MVP 数据量小，暂时不分页。 */
    fun observeAll(): Flow<List<BehaviorEvent>>

    fun observeCountBetween(start: Instant, end: Instant): Flow<Int>

    /** 一次性计数，供桌面小组件刷新时使用。 */
    suspend fun countBetween(start: Instant, end: Instant): Int

    suspend fun record(occurredAt: Instant, note: String? = null): Long

    suspend fun updateEvent(id: Long, occurredAt: Instant, note: String?)

    suspend fun deleteEvent(id: Long)
}
