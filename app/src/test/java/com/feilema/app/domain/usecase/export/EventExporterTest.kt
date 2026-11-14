package com.feilema.app.domain.usecase.export

import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventExporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val exportedAt = Instant.parse("2026-11-13T03:30:00Z")

    private val simpleEvent = BehaviorEvent(
        id = 7L,
        occurredAt = Instant.parse("2026-11-13T12:00:00Z"), // 本地 20:00
        createdAt = Instant.parse("2026-11-13T12:00:05Z"),
        note = null,
    )

    private val trickyEvent = BehaviorEvent(
        id = 8L,
        occurredAt = Instant.parse("2026-11-13T11:00:00Z"),
        createdAt = Instant.parse("2026-11-13T11:00:05Z"),
        note = "  含逗号, 引号\" 换行\n结束",
    )

    @Test
    fun `csv contains header and local readable time`() {
        val csv = EventExporter.toCsv(listOf(simpleEvent), zone)

        assertTrue(csv.startsWith("id,发生时间,记录时间,备注"))
        assertTrue(csv.contains("2026-11-13 20:00:00"))
        assertTrue(csv.contains("2026-11-13 20:00:05"))
    }

    @Test
    fun `csv sorts rows by occurredAt`() {
        val earlier = simpleEvent.copy(id = 8L, occurredAt = Instant.parse("2026-11-13T11:00:00Z"))
        val csv = EventExporter.toCsv(listOf(simpleEvent, earlier), zone)
        val rows = csv.lines().filter { it.isNotBlank() }

        assertEquals(3, rows.size)
        // earlier 发生在 19:00，比 simpleEvent 的 20:00 早，应排在前面。
        assertTrue(rows[1].startsWith("8,"))
        assertTrue(rows[2].startsWith("7,"))
    }

    @Test
    fun `csv quotes comma quote and newline`() {
        val csv = EventExporter.toCsv(listOf(trickyEvent), zone)

        assertTrue(csv.contains("\"  含逗号, 引号\"\" 换行\n结束\""))
    }

    @Test
    fun `csv keeps empty note as empty field`() {
        val line = EventExporter.toCsv(listOf(simpleEvent), zone).lines().first { it.startsWith("7,") }

        assertTrue(line.endsWith(","))
    }

    @Test
    fun `json contains metadata and null note`() {
        val json = EventExporter.toJson(listOf(simpleEvent), exportedAt)

        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(json.contains("\"exportedAt\": \"2026-11-13T03:30:00Z\""))
        assertTrue(json.contains("\"count\": 1"))
        assertTrue(json.contains("\"note\": null"))
        assertTrue(json.contains("\"occurredAt\": \"2026-11-13T12:00:00Z\""))
        assertFalse(json.contains("\"events\": []"))
    }

    @Test
    fun `json escapes special characters in note`() {
        val json = EventExporter.toJson(listOf(trickyEvent), exportedAt)

        // 真实 JSON 文本里是 \" 和 \n 两个转义序列。
        assertTrue(json.contains("""含逗号, 引号\" 换行\n结束"""))
    }

    @Test
    fun `json writes empty event list`() {
        val json = EventExporter.toJson(emptyList(), exportedAt)

        assertTrue(json.contains("\"count\": 0"))
        assertTrue(json.contains("\"events\": []"))
    }
}
