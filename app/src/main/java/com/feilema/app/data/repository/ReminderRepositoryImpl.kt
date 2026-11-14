package com.feilema.app.data.repository

import android.content.Context
import com.feilema.app.data.reminder.ReminderNotifications
import com.feilema.app.data.reminder.ReminderPreferences
import com.feilema.app.data.reminder.ReminderScheduler
import com.feilema.app.domain.model.ReminderSettings
import com.feilema.app.domain.repository.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ReminderPreferences,
) : ReminderRepository {

    override val settings: Flow<ReminderSettings> = preferences.settings

    override suspend fun setEnabled(enabled: Boolean) {
        preferences.setEnabled(enabled)
        if (enabled) {
            ReminderNotifications.ensureChannel(context)
            ReminderScheduler.schedule(context, preferences.settings.value.time)
        } else {
            ReminderScheduler.cancel(context)
        }
    }

    override suspend fun setTime(time: LocalTime) {
        preferences.setTime(time)
        if (preferences.settings.value.enabled) {
            ReminderScheduler.schedule(context, time)
        }
    }
}
