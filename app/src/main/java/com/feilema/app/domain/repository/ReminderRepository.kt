package com.feilema.app.domain.repository

import com.feilema.app.domain.model.ReminderSettings
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {

    val settings: Flow<ReminderSettings>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTime(time: LocalTime)
}
