package com.feilema.app.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 编辑弹窗的两种入口：新增补录 / 修改已有记录。 */
private sealed interface EditorTarget {
    data object Add : EditorTarget
    data class Edit(val event: BehaviorEvent) : EditorTarget
}

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onBackfill = viewModel::addBackfilled,
        onUpdate = viewModel::updateEvent,
        onDelete = viewModel::deleteEvent,
    )
}

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onBackfill: (LocalDate, LocalTime, String?) -> Unit,
    onUpdate: (Long, LocalDate, LocalTime, String?) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<BehaviorEvent?>(null) }
    val today = remember { LocalDate.now() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = "历史",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uiState.days.isEmpty()) {
                        "还没有记录"
                    } else {
                        "共 ${uiState.totalCount} 次 · ${uiState.days.size} 天有记录"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.days.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有记录。点右下角「补录」，可以补记以前发生的行为。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    uiState.days.forEach { day ->
                        item(key = "day-${day.date}") {
                            DayHeader(day = day, today = today)
                        }
                        items(items = day.events, key = { event -> "event-${event.id}" }) { event ->
                            EventRow(
                                event = event,
                                onClick = { editorTarget = EditorTarget.Edit(event) },
                                onDeleteClick = { pendingDelete = event },
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editorTarget = EditorTarget.Add },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Text("补录")
        }
    }

    val target = editorTarget
    if (target != null) {
        val zone = remember(target) { ZoneId.systemDefault() }
        val nowTime = remember(target) { LocalTime.now().withSecond(0).withNano(0) }
        when (target) {
            is EditorTarget.Add -> EventEditorDialog(
                title = "补录一次",
                initialDate = today,
                initialTime = nowTime,
                initialNote = null,
                onDismiss = { editorTarget = null },
                onConfirm = { date, time, note ->
                    onBackfill(date, time, note)
                    editorTarget = null
                },
            )

            is EditorTarget.Edit -> {
                val localDateTime = target.event.occurredAt.atZone(zone)
                EventEditorDialog(
                    title = "修改记录",
                    initialDate = localDateTime.toLocalDate(),
                    initialTime = localDateTime.toLocalTime().withSecond(0).withNano(0),
                    initialNote = target.event.note,
                    onDismiss = { editorTarget = null },
                    onConfirm = { date, time, note ->
                        onUpdate(target.event.id, date, time, note)
                        editorTarget = null
                    },
                )
            }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("删除后无法恢复，统计会随之更新。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleting.id)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DayHeader(day: HistoryDay, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayLabel(day.date, today),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "${day.count} 次",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventRow(
    event: BehaviorEvent,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = event.occurredAt.atZone(ZoneId.systemDefault()).toLocalTime().format(TimeFormatter),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = event.note ?: "一次记录",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDeleteClick) {
            Text("删除")
        }
    }
}

/** 新增/修改共用：选日期 + 选时间 + 可选备注。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    title: String,
    initialDate: LocalDate,
    initialTime: LocalTime,
    initialNote: String?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime, String?) -> Unit,
) {
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(initialTime) }
    var note by remember { mutableStateOf(initialNote.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextButton(onClick = { showDatePicker = true }) {
                    Text("日期：${date.format(DateLabelFormatter)}")
                }
                TextButton(onClick = { showTimePicker = true }) {
                    Text("时间：${time.format(TimeFormatter)}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(date, time, note.trim().ifBlank { null })
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { date = it.toUtcLocalDate() }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = { TimeInput(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        time = LocalTime.of(timeState.hour, timeState.minute)
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "今天"
    today.minusDays(1) -> "昨天"
    else -> date.format(DateLabelFormatter)
}

// DatePicker 的 selectedDateMillis 固定按 UTC 零点表达“哪一天”，这里做互转。
private fun LocalDate.toUtcEpochMilli(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
