package com.feilema.app.feature.home

import com.feilema.app.domain.model.BehaviorEvent

data class HomeUiState(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val recentEvents: List<BehaviorEvent> = emptyList(),
) {
    val canUndo: Boolean
        get() = recentEvents.isNotEmpty()
}
