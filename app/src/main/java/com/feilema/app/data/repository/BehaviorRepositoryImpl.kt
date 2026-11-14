package com.feilema.app.data.repository

import com.feilema.app.data.local.dao.BehaviorEventDao
import com.feilema.app.data.local.entity.BehaviorEventEntity
import com.feilema.app.data.mapper.toDomain
import com.feilema.app.domain.model.BehaviorEvent
import com.feilema.app.domain.repository.BehaviorRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BehaviorRepositoryImpl @Inject constructor(
    private val dao: BehaviorEventDao,
) : BehaviorRepository {

    override fun observeRecent(limit: Int): Flow<List<BehaviorEvent>> =
        dao.observeRecent(limit).map { entities -> entities.map(BehaviorEventEntity::toDomain) }

    override fun observeAll(): Flow<List<BehaviorEvent>> =
        dao.observeAll().map { entities -> entities.map(BehaviorEventEntity::toDomain) }

    override fun observeCountBetween(start: Instant, end: Instant): Flow<Int> =
        dao.observeCountBetween(start.toEpochMilli(), end.toEpochMilli())

    override suspend fun countBetween(start: Instant, end: Instant): Int =
        dao.countBetween(start.toEpochMilli(), end.toEpochMilli())

    override suspend fun record(occurredAt: Instant, note: String?): Long =
        dao.insert(
            BehaviorEventEntity(
                occurredAt = occurredAt.toEpochMilli(),
                createdAt = System.currentTimeMillis(),
                note = note,
            ),
        )

    override suspend fun updateEvent(id: Long, occurredAt: Instant, note: String?) {
        dao.update(id = id, occurredAt = occurredAt.toEpochMilli(), note = note)
    }

    override suspend fun deleteEvent(id: Long) {
        dao.deleteById(id)
    }
}
