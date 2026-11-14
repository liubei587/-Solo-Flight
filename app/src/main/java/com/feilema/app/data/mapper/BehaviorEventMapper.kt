package com.feilema.app.data.mapper

import com.feilema.app.data.local.entity.BehaviorEventEntity
import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant

fun BehaviorEventEntity.toDomain(): BehaviorEvent = BehaviorEvent(
    id = id,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    createdAt = Instant.ofEpochMilli(createdAt),
    note = note,
)
