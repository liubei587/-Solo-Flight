package com.feilema.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feilema.app.domain.repository.BehaviorRepository
import com.feilema.app.feature.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: BehaviorRepository,
    private val widgetRefresher: WidgetRefresher,
    private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId = clock.zone

    // 今天：[00:00, 明天 00:00)；本周：[本周一 00:00, 下周一 00:00)。
    // 等有一次真实使用后再处理"跨零点自动刷新"。
    private val today: LocalDate = LocalDate.now(clock)
    private val todayStart: Instant = today.atStartOfDay(zone).toInstant()
    private val tomorrowStart: Instant = today.plusDays(1).atStartOfDay(zone).toInstant()
    private val weekStartDate: LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val weekStart: Instant = weekStartDate.atStartOfDay(zone).toInstant()
    private val nextWeekStart: Instant =
        weekStartDate.plusWeeks(1).atStartOfDay(zone).toInstant()

    /** 首页日历当前展示的月份；默认本月，可以往前翻。 */
    private val calendarMonth = MutableStateFlow(YearMonth.now(clock))

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        repository.observeCountBetween(todayStart, tomorrowStart),
        repository.observeCountBetween(weekStart, nextWeekStart),
        calendarMonth,
    ) { events, todayCount, weekCount, month ->
        val monthCounts = events.asSequence()
            .map { it.occurredAt.atZone(zone) }
            .filter { YearMonth.from(it) == month }
            .groupingBy { it.toLocalDate() }
            .eachCount()
        HomeUiState(
            todayCount = todayCount,
            weekCount = weekCount,
            recentEvents = events.take(3),
            calendarMonth = month,
            monthCounts = monthCounts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun previousMonth() {
        calendarMonth.value = calendarMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = calendarMonth.value.plusMonths(1)
        if (!next.isAfter(YearMonth.now(clock))) {
            calendarMonth.value = next
        }
    }

    fun record() {
        viewModelScope.launch {
            repository.record(occurredAt = Instant.now(clock))
            widgetRefresher.refresh()
        }
    }

    fun undoLast() {
        val latest = uiState.value.recentEvents.firstOrNull() ?: return
        viewModelScope.launch {
            repository.deleteEvent(latest.id)
            widgetRefresher.refresh()
        }
    }
}
