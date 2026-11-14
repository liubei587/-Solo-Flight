package com.feilema.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feilema.app.core.ui.theme.ThemeMode
import com.feilema.app.core.ui.theme.ThemePalette
import com.feilema.app.data.launcher.LauncherIconController
import com.feilema.app.data.preferences.ThemePreferences
import com.feilema.app.domain.model.BehaviorEvent
import com.feilema.app.domain.repository.BehaviorRepository
import com.feilema.app.domain.repository.ReminderRepository
import com.feilema.app.domain.usecase.export.EventExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    repository: BehaviorRepository,
    private val reminderRepository: ReminderRepository,
    private val themePreferences: ThemePreferences,
    private val launcherIconController: LauncherIconController,
    private val clock: Clock,
) : ViewModel() {

    private val events: StateFlow<List<BehaviorEvent>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(
            events,
            reminderRepository.settings,
            themePreferences.settings,
            message,
        ) { allEvents, reminder, theme, currentMessage ->
            val recordedDates = allEvents
                .map { it.occurredAt.atZone(clock.zone).toLocalDate() }
                .toSet()
            SettingsUiState(
                totalCount = allEvents.size,
                recordedDays = recordedDates.size,
                firstDate = recordedDates.minOrNull(),
                reminder = reminder,
                theme = theme,
                message = currentMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { reminderRepository.setEnabled(enabled) }
    }

    fun setReminderTime(time: LocalTime) {
        viewModelScope.launch { reminderRepository.setTime(time) }
    }

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setMode(mode)
    }

    fun setThemePalette(palette: ThemePalette) {
        themePreferences.setPalette(palette)
        applyLauncherIcon()
    }

    fun setCustomThemeColor(color: Long) {
        themePreferences.setCustomColor(color)
        applyLauncherIcon()
    }

    fun setBackgroundUri(uri: String?) {
        themePreferences.setBackgroundUri(uri)
    }

    private fun applyLauncherIcon() {
        val settings = themePreferences.settings.value
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { launcherIconController.apply(settings) }
        }
    }

    fun exportCsv(): String = EventExporter.toCsv(events.value, clock.zone)

    fun exportJson(): String = EventExporter.toJson(events.value, Instant.now(clock))

    fun postMessage(message: String?) {
        this.message.value = message
    }
}
