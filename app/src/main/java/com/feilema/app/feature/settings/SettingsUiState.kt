package com.feilema.app.feature.settings

import com.feilema.app.data.preferences.ThemeSettings
import com.feilema.app.domain.model.ReminderSettings
import java.time.LocalDate

data class SettingsUiState(
    val totalCount: Int = 0,
    val recordedDays: Int = 0,
    val firstDate: LocalDate? = null,
    val reminder: ReminderSettings = ReminderSettings(),
    val theme: ThemeSettings = ThemeSettings(),
    val message: String? = null,
)
