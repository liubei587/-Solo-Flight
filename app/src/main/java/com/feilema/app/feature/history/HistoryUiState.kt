package com.feilema.app.feature.history

import com.feilema.app.domain.model.BehaviorEvent
import java.time.LocalDate

/** 时间线上的一天：按本地时区自然日分组。 */
data class HistoryDay(
    val date: LocalDate,
    val events: List<BehaviorEvent>,
) {
    val count: Int get() = events.size
}

data class HistoryUiState(
    val days: List<HistoryDay> = emptyList(),
    val totalCount: Int = 0,
)
