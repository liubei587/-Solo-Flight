package com.feilema.app.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feilema.app.BuildConfig
import com.feilema.app.core.ui.components.ColorPickerDialog
import com.feilema.app.core.ui.theme.ThemeMode
import com.feilema.app.core.ui.theme.ThemePalette
import com.feilema.app.core.ui.theme.White
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA)
private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

private val RainbowBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFF43F5E),
        Color(0xFFF59E0B),
        Color(0xFF22C55E),
        Color(0xFF06B6D4),
        Color(0xFF3B82F6),
        Color(0xFF8B5CF6),
        Color(0xFFF43F5E),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showTimePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var pendingCsv by remember { mutableStateOf("") }
    var pendingJson by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setReminderEnabled(true)
        } else {
            viewModel.postMessage("没有通知权限，提醒不会显示；可以在系统设置里开启。")
        }
    }

    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    // 裁剪取景框的宽高比 = 背景实际显示区域（窗口根视图）的宽高比，
    // 否则裁出来的图片铺到背景上会被拉伸。
    var cropAspect by remember { mutableStateOf(0f) }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    runCatching { decodeBackgroundForCrop(context, uri) }.getOrNull()
                }
                if (bitmap != null) {
                    val root = view.rootView
                    cropAspect = if (root.width > 0 && root.height > 0) {
                        root.width.toFloat() / root.height.toFloat()
                    } else {
                        0f
                    }
                    cropSource = bitmap
                } else {
                    viewModel.postMessage("读取图片失败，请换一张试试")
                }
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val content = pendingCsv
            scope.launch {
                val success = writeText(context, uri, content)
                viewModel.postMessage(if (success) "CSV 已导出" else "导出失败，请换个目录再试")
            }
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val content = pendingJson
            scope.launch {
                val success = writeText(context, uri, content)
                viewModel.postMessage(if (success) "JSON 已导出" else "导出失败，请换个目录再试")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching { readText(context, uri) }.getOrNull()
                if (text != null) {
                    viewModel.importData(text)
                } else {
                    viewModel.postMessage("读取文件失败，请换一个文件试试")
                }
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.postMessage(null)
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            initial = uiState.reminder.time,
            onConfirm = { time ->
                viewModel.setReminderTime(time)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = uiState.theme.customColor,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                viewModel.setCustomThemeColor(color)
                showColorPicker = false
            },
        )
    }

    cropSource?.let { bitmap ->
        BackgroundCropDialog(
            source = bitmap,
            targetAspect = cropAspect,
            onDismiss = { cropSource = null },
            onCropped = { cropped ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        saveBackgroundBitmap(context, cropped)
                    }
                    cropSource = null
                    if (path != null) {
                        viewModel.setBackgroundUri(path)
                        viewModel.postMessage("背景已更新，回到首页看看效果")
                    } else {
                        viewModel.postMessage("保存背景失败，请重试")
                    }
                }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsSection("数据概况") {
                    DataRow("总记录", "${uiState.totalCount} 次")
                    DataRow("有记录的天数", "${uiState.recordedDays} 天")
                    DataRow(
                        "第一条记录",
                        uiState.firstDate?.format(DATE_FORMATTER) ?: "还没有记录",
                    )
                }
            }

            item {
                SettingsSection("导出备份") {
                    Text(
                        "导出后自己保存或换机导入；应用不会上传任何数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                pendingCsv = viewModel.exportCsv()
                                csvLauncher.launch("feilema-${LocalDate.now()}.csv")
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 CSV") }
                        OutlinedButton(
                            onClick = {
                                pendingJson = viewModel.exportJson()
                                jsonLauncher.launch("feilema-${LocalDate.now()}.json")
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("导出 JSON") }
                    }
                    OutlinedButton(
                        onClick = {
                            // 有些文件管理器/网盘会把 .json/.csv 报成未知 MIME，
                            // 这里放开为所有文件，导入器会自行识别 JSON / CSV。
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("导入备份（JSON / CSV）") }
                    Text(
                        "支持本应用导出的 JSON / CSV；如果文件选择器里看不到文件，请切换到“全部文件”。重复记录会自动跳过。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.totalCount == 0) {
                        Text(
                            "还没有记录，导出文件会是空表。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SettingsSection("每日提醒") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("每天提醒记录")
                            Text(
                                if (uiState.reminder.enabled) {
                                    "每天 ${uiState.reminder.time.format(TIME_FORMATTER)} 提醒一次"
                                } else {
                                    "关闭"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = uiState.reminder.enabled,
                            onCheckedChange = { checked ->
                                val needsPermission = checked &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setReminderEnabled(checked)
                                }
                            },
                        )
                    }
                    if (uiState.reminder.enabled) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("提醒时间", modifier = Modifier.weight(1f))
                            Text(
                                uiState.reminder.time.format(TIME_FORMATTER),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection("外观") {
                    Text(
                        "跟随系统会随手机的深色模式自动切换，也可以固定使用浅色或深色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.theme.mode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }

                    HorizontalDivider()

                    Text("主题色", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThemePalette.entries.forEach { palette ->
                            PaletteCircle(
                                color = Color(palette.seed),
                                selected = uiState.theme.customColor == null &&
                                    uiState.theme.palette == palette,
                                onClick = { viewModel.setThemePalette(palette) },
                            )
                        }
                        CustomColorCircle(
                            selected = uiState.theme.customColor != null,
                            onClick = { showColorPicker = true },
                        )
                    }
                    Text(
                        if (uiState.theme.customColor != null) {
                            "当前使用自定义颜色，点最后一个圆点可以重新调色。"
                        } else {
                            "最后一个彩色圆点可以自己调色：色相、饱和度、明度都能拖。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()

                    Text("背景图", fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            uiState.theme.backgroundUri != null ->
                                "已设置自定义背景，页面会盖一层主题色遮罩保证可读。"

                            else -> "默认使用主题色渐变，也可以选一张本地图片。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { backgroundLauncher.launch(arrayOf("image/*")) },
                            modifier = Modifier.weight(1f),
                        ) { Text("选择图片背景") }
                        if (uiState.theme.backgroundUri != null) {
                            OutlinedButton(
                                onClick = { viewModel.setBackgroundUri(null) },
                                modifier = Modifier.weight(1f),
                            ) { Text("清除背景") }
                        }
                    }
                    Text(
                        "主题色切换后，桌面图标颜色也会跟着变（自定义颜色会换成彩色图标）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection("隐私") {
                    Text(
                        "数据只保存在这台手机（Room 数据库 + 本地设置），应用不申请网络权限，也不会上传。" +
                            "卸载应用会删除数据，建议定期导出备份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection("关于") {
                    DataRow("版本", BuildConfig.VERSION_NAME)
                    DataRow("包名", BuildConfig.APPLICATION_ID)
                }
            }
        }
    }
}

@Composable
private fun PaletteCircle(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CustomColorCircle(selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(RainbowBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒时间") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                TimeInput(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private suspend fun writeText(context: Context, uri: Uri, content: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("无法打开目标文件")
        }.isSuccess
    }

private suspend fun readText(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("无法打开文件")
        text.trimStart('\uFEFF')
    }
