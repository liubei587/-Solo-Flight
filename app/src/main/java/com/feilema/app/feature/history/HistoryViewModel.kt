package com.feilema.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feilema.app.domain.model.BehaviorEvent
import com.feilema.app.domain.repository.BehaviorRepository
import com.feilema.app.feature.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: BehaviorRepository,
    private val widgetRefresher: WidgetRefresher,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = clock.zone

    val uiState: StateFlow<HistoryUiState> = repository.observeAll()
        .map { events -> toUiState(events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    private fun toUiState(events: List<BehaviorEvent>): HistoryUiState {
        val days = events
            .groupBy { it.occurredAt.atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayEvents) -> HistoryDay(date, dayEvents.sortedByDescending { it.occurredAt }) }
        return HistoryUiState(days = days, totalCount = events.size)
    }

    /** 补录：把过去某个日期/时间的一次行为补进事件流。 */
    fun addBackfilled(date: LocalDate, time: LocalTime, note: String?) {
        viewModelScope.launch {
            repository.record(
                occurredAt = date.atTime(time).atZone(zone).toInstant(),
                note = note,
            )
            widgetRefresher.refresh()
        }
    }

    /** 修改发生时间/备注，统计口径随事件流自动重算。 */
    fun updateEvent(id: Long, date: LocalDate, time: LocalTime, note: String?) {
        viewModelScope.launch {
            repository.updateEvent(
                id = id,
                occurredAt = date.atTime(time).atZone(zone).toInstant(),
                note = note,
            )
            widgetRefresher.refresh()
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            repository.deleteEvent(id)
            widgetRefresher.refresh()
        }
    }
}
