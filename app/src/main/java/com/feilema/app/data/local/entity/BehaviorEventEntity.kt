package com.feilema.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 事件流：一行代表"发生了一次"，不是"次数"。
 * 统计由 SQL 实时聚合，不落统计表。
 */
@Entity(tableName = "behavior_events", indices = [Index("occurredAt")])
data class BehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val typeId: Long = DEFAULT_TYPE_ID,
    val occurredAt: Long,
    val createdAt: Long,
    val note: String? = null,
) {
    companion object {
        /** 单行为 MVP 的默认类型；多行为版本从 behavior_types 表读取。 */
        const val DEFAULT_TYPE_ID = 1L
    }
}
