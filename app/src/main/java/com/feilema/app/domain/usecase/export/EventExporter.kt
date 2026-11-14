package com.feilema.app.domain.usecase.export

import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 把全部事件导出成 CSV / JSON 文本。
 *
 * 纯 Kotlin（不依赖 Android），方便用固定时间做 JVM 单测；真正写文件由 UI 层
 * 通过 SAF（ActivityResultContracts.CreateDocument）完成。
 */
object EventExporter {

    private val READABLE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    /** CSV 列：id、发生时间、记录时间、备注。按发生时间升序，方便用表格软件看。 */
    fun toCsv(events: List<BehaviorEvent>, zone: ZoneId): String {
        val builder = StringBuilder()
        builder.appendLine("id,发生时间,记录时间,备注")
        events.sortedBy { it.occurredAt }.forEach { event ->
            builder
                .append(event.id).append(',')
                .append(csvField(event.occurredAt.atZone(zone).format(READABLE_FORMATTER))).append(',')
                .append(csvField(event.createdAt.atZone(zone).format(READABLE_FORMATTER))).append(',')
                .append(csvField(event.note.orEmpty()))
                .appendLine()
        }
        return builder.toString()
    }

    /**
     * JSON 导出：时间用 ISO-8601（UTC）保存，跨时区不丢信息；
     * 备份/恢复时按原样读取即可。
     */
    fun toJson(events: List<BehaviorEvent>, exportedAt: Instant): String {
        val sorted = events.sortedBy { it.occurredAt }
        val builder = StringBuilder()
        builder.appendLine("{")
        builder.append("  \"app\": \"飞了吗\",\n")
        builder.append("  \"formatVersion\": 1,\n")
        builder.append("  \"exportedAt\": ").append(jsonString(exportedAt.toString())).append(",\n")
        builder.append("  \"count\": ").append(sorted.size).append(",\n")
        if (sorted.isEmpty()) {
            builder.append("  \"events\": []\n")
        } else {
            builder.append("  \"events\": [\n")
            sorted.forEachIndexed { index, event ->
                builder.append("    {\"id\": ").append(event.id)
                    .append(", \"occurredAt\": ").append(jsonString(event.occurredAt.toString()))
                    .append(", \"createdAt\": ").append(jsonString(event.createdAt.toString()))
                    .append(", \"note\": ").append(event.note?.let(::jsonString) ?: "null")
                    .append('}')
                if (index != sorted.lastIndex) builder.append(',')
                builder.appendLine()
            }
            builder.append("  ]\n")
        }
        builder.appendLine("}")
        return builder.toString()
    }

    private fun csvField(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun jsonString(value: String): String {
        val builder = StringBuilder("\"")
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> if (character < ' ') {
                    builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(character)
                }
            }
        }
        return builder.append('"').toString()
    }
}
