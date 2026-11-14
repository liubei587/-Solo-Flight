package com.feilema.app.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feilema.app.data.preferences.StatsPreferences
import com.feilema.app.domain.model.StatsLayout
import com.feilema.app.domain.repository.BehaviorRepository
import com.feilema.app.domain.usecase.stats.StatsCalculator
import com.feilema.app.domain.usecase.stats.StatsPageData
import com.feilema.app.domain.usecase.stats.StatsRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val page: StatsPageData = StatsPageData(),
    val chartRange: StatsRange = StatsRange.MONTH,
    val metricRange: StatsRange = StatsRange.MONTH,
    val layout: StatsLayout = StatsLayout(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: BehaviorRepository,
    private val statsPreferences: StatsPreferences,
    private val clock: Clock,
) : ViewModel() {

    private val chartRange = MutableStateFlow(StatsRange.MONTH)
    private val metricRange = MutableStateFlow(StatsRange.MONTH)

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeAll(),
        chartRange,
        metricRange,
        statsPreferences.layout,
    ) { events, selectedChartRange, selectedMetricRange, layout ->
        StatsUiState(
            page = StatsCalculator.buildPage(
                events = events,
                now = Instant.now(clock),
                zone = clock.zone,
                chartRange = selectedChartRange,
                metricRange = selectedMetricRange,
            ),
            chartRange = selectedChartRange,
            metricRange = selectedMetricRange,
            layout = layout,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun setChartRange(range: StatsRange) {
        chartRange.value = range
    }

    fun setMetricRange(range: StatsRange) {
        metricRange.value = range
    }

    fun saveLayout(layout: StatsLayout) {
        statsPreferences.saveLayout(layout)
    }

    fun resetLayout() {
        statsPreferences.reset()
    }
}
