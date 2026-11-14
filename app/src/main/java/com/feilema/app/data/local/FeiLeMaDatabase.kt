package com.feilema.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.feilema.app.data.local.dao.BehaviorEventDao
import com.feilema.app.data.local.entity.BehaviorEventEntity

@Database(
    entities = [BehaviorEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FeiLeMaDatabase : RoomDatabase() {
    abstract fun behaviorEventDao(): BehaviorEventDao
}
