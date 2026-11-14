package com.feilema.app.data.reminder

import android.content.Context
import android.content.SharedPreferences
import com.feilema.app.domain.model.ReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 提醒设置的本地存储（SharedPreferences）。数据很小，不需要进 Room。
 * 接收器（提醒/开机）用 [read] 直接读，不依赖 Hilt。
 */
@Singleton
class ReminderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read(preferences))
    val settings: StateFlow<ReminderSettings> = _settings.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settings.update { it.copy(enabled = enabled) }
    }

    fun setTime(time: LocalTime) {
        preferences.edit().putInt(KEY_MINUTE_OF_DAY, time.hour * 60 + time.minute).apply()
        _settings.update { it.copy(time = time) }
    }

    companion object {
        private const val PREFERENCES_NAME = "reminder_prefs"
        private const val KEY_ENABLED = "reminder_enabled"
        private const val KEY_MINUTE_OF_DAY = "reminder_minute_of_day"
        private const val DEFAULT_MINUTE_OF_DAY = 20 * 60
        private const val MINUTES_PER_DAY = 24 * 60

        /** 供 BroadcastReceiver 等非 Hilt 场景读取。 */
        fun read(context: Context): ReminderSettings =
            read(context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))

        private fun read(preferences: SharedPreferences): ReminderSettings {
            val minuteOfDay = preferences.getInt(KEY_MINUTE_OF_DAY, DEFAULT_MINUTE_OF_DAY)
                .coerceIn(0, MINUTES_PER_DAY - 1)
            return ReminderSettings(
                enabled = preferences.getBoolean(KEY_ENABLED, false),
                time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
            )
        }
    }
}
