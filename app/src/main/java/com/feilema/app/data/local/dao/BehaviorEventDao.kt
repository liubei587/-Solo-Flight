package com.feilema.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.feilema.app.data.local.entity.BehaviorEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorEventDao {

    @Insert
    suspend fun insert(event: BehaviorEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<BehaviorEventEntity>): List<Long>

    /** 一次性读取全部事件，导入时用来做去重。 */
    @Query("SELECT * FROM behavior_events ORDER BY occurredAt DESC, id DESC")
    suspend fun getAllOnce(): List<BehaviorEventEntity>

    @Query("DELETE FROM behavior_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM behavior_events ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BehaviorEventEntity>>

    @Query("SELECT * FROM behavior_events ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<BehaviorEventEntity>>

    @Query("UPDATE behavior_events SET occurredAt = :occurredAt, note = :note WHERE id = :id")
    suspend fun update(id: Long, occurredAt: Long, note: String?)

    @Query(
        "SELECT COUNT(*) FROM behavior_events " +
            "WHERE occurredAt >= :start AND occurredAt < :end"
    )
    fun observeCountBetween(start: Long, end: Long): Flow<Int>

    /** 一次性计数，给桌面小组件用（小组件不常驻，不需要 Flow）。 */
    @Query(
        "SELECT COUNT(*) FROM behavior_events " +
            "WHERE occurredAt >= :start AND occurredAt < :end"
    )
    suspend fun countBetween(start: Long, end: Long): Int
}
