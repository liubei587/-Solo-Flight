package com.feilema.app.domain.usecase.export

import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** 导入失败时抛出的可读异常，message 会直接展示给用户。 */
class EventImportException(message: String) : Exception(message)

/**
 * 一次解析的结果。
 *
 * @param events 解析出的有效事件（id 固定为 0，写库时由 Room 重新分配）
 * @param invalid 无法识别的行数
 * @param format 识别到的文件格式（JSON / CSV）
 */
data class ImportedEvents(
    val events: List<BehaviorEvent>,
    val invalid: Int,
    val format: String,
)

/**
 * 解析本应用导出的 JSON / CSV 备份。
 *
 * 纯 Kotlin（不依赖 Android 组件），可以被 JVM 单测直接覆盖；JSON 用 Android 自带的 org.json。
 */
object EventImporter {

    private val CSV_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 自动识别格式：以 { 或 [ 开头按 JSON，否则按 CSV。 */
    fun parse(text: String, zone: ZoneId): ImportedEvents {
        val trimmed = text.trim().trimStart('\uFEFF').trim()
        if (trimmed.isEmpty()) {
            throw EventImportException("文件是空的")
        }
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseJson(trimmed, zone)
        } else {
            parseCsv(trimmed, zone)
        }
    }

    fun parseJson(text: String, zone: ZoneId): ImportedEvents {
        val array: JSONArray = try {
            if (text.startsWith("[")) {
                JSONArray(text)
            } else {
                val root = JSONObject(text)
                val declaredVersion = root.optInt("formatVersion", 1)
                if (declaredVersion > 1) {
                    throw EventImportException(
                        "备份文件版本较新（formatVersion=$declaredVersion），请先升级应用",
                    )
                }
                root.optJSONArray("events")
                    ?: throw EventImportException("这个 JSON 里没有 events 数组，可能不是本应用导出的备份")
            }
        } catch (e: JSONException) {
            throw EventImportException("JSON 格式不正确：${e.message ?: "无法解析"}")
        }

        val events = ArrayList<BehaviorEvent>(array.length())
        var invalid = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                invalid++
                continue
            }
            val occurredAt = parseInstant(item.opt("occurredAt"), zone)
            if (occurredAt == null) {
                invalid++
                continue
            }
            val createdAt = parseInstant(item.opt("createdAt"), zone) ?: occurredAt
            val note = (item.opt("note") as? String)?.trim()?.ifBlank { null }
            events += BehaviorEvent(
                id = 0L,
                occurredAt = occurredAt,
                createdAt = createdAt,
                note = note,
            )
        }
        return ImportedEvents(events = events, invalid = invalid, format = "JSON")
    }

    fun parseCsv(text: String, zone: ZoneId): ImportedEvents {
        val rows = readCsvRows(text)
        if (rows.isEmpty()) {
            throw EventImportException("CSV 文件是空的")
        }
        val first = rows.first()
        val hasHeader = (first.size >= 2 && first[1].contains("发生时间")) ||
            first.firstOrNull()?.trim().equals("id", ignoreCase = true)
        val start = if (hasHeader) 1 else 0

        val events = ArrayList<BehaviorEvent>()
        var invalid = 0
        for (index in start until rows.size) {
            val row = rows[index]
            if (row.all { it.isBlank() }) continue
            if (row.size < 2) {
                invalid++
                continue
            }
            val occurredAt = parseCsvTime(row.getOrNull(1), zone)
            if (occurredAt == null) {
                invalid++
                continue
            }
            val createdAt = parseCsvTime(row.getOrNull(2), zone) ?: occurredAt
            val note = row.getOrNull(3)?.trim()?.ifBlank { null }
            events += BehaviorEvent(
                id = 0L,
                occurredAt = occurredAt,
                createdAt = createdAt,
                note = note,
            )
        }
        return ImportedEvents(events = events, invalid = invalid, format = "CSV")
    }

    private fun parseCsvTime(value: String?, zone: ZoneId): Instant? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return parseInstant(trimmed, zone)
    }

    private fun parseInstant(value: Any?, zone: ZoneId): Instant? {
        return when (value) {
            null -> null
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    runCatching { Instant.parse(trimmed) }.getOrNull()
                        ?: runCatching {
                            LocalDateTime.parse(trimmed, CSV_TIME_FORMATTER)
                                .atZone(zone)
                                .toInstant()
                        }.getOrNull()
                }
            }
            is Number -> Instant.ofEpochMilli(value.toLong())
            else -> null
        }
    }

    /**
     * RFC 4180 风格的 CSV 解析：支持引号字段、字段内逗号 / 换行 / 双引号。
     */
    private fun readCsvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = ArrayList()
        }

        while (index < text.length) {
            val character = text[index]
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(character)
                }
            } else {
                when (character) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\r' -> {
                        if (index + 1 < text.length && text[index + 1] == '\n') index++
                        endRow()
                    }
                    '\n' -> endRow()
                    else -> field.append(character)
                }
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            endRow()
        }
        return rows
    }
}
