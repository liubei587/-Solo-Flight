package com.feilema.app.domain.usecase.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.feilema.app.domain.model.BehaviorEvent
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class EventImporterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val exportedAt = Instant.parse("2026-11-13T03:30:00Z")

    private val events = listOf(
        BehaviorEvent(
            id = 1L,
            occurredAt = Instant.parse("2026-11-13T12:00:00Z"),
            createdAt = Instant.parse("2026-11-13T12:00:05Z"),
            note = null,
        ),
        BehaviorEvent(
            id = 2L,
            occurredAt = Instant.parse("2026-10-01T01:00:00Z"),
            createdAt = Instant.parse("2026-10-01T01:00:03Z"),
            note = "含逗号, 引号\" 换行\n结束",
        ),
        BehaviorEvent(
            id = 3L,
            occurredAt = Instant.parse("2026-11-09T16:30:00Z"),
            createdAt = Instant.parse("2026-11-09T16:30:02Z"),
            note = "补录",
        ),
    )

    @Test
    fun `json round trip keeps occurredAt createdAt and note`() {
        val json = EventExporter.toJson(events, exportedAt)

        val imported = EventImporter.parse(json, zone)

        assertEquals("JSON", imported.format)
        assertEquals(0, imported.invalid)
        assertEquals(events.size, imported.events.size)
        // 导出时按 occurredAt 升序排列，导入顺序与文件一致
        val expectedEvents = events.sortedBy { it.occurredAt }
        imported.events.zip(expectedEvents).forEach { (actual, expected) ->
            assertEquals(expected.occurredAt, actual.occurredAt)
            assertEquals(expected.createdAt, actual.createdAt)
            assertEquals(expected.note?.trim(), actual.note)
        }
    }

    @Test
    fun `csv round trip keeps times and tricky note`() {
        val csv = EventExporter.toCsv(events, zone)

        val imported = EventImporter.parse(csv, zone)

        assertEquals("CSV", imported.format)
        assertEquals(0, imported.invalid)
        assertEquals(events.size, imported.events.size)
        val tricky = imported.events.first { it.note?.contains("换行") == true }
        assertEquals("含逗号, 引号\" 换行\n结束", tricky.note)
        assertEquals(Instant.parse("2026-10-01T01:00:00Z"), tricky.occurredAt)
    }

    @Test
    fun `accepts top level json array`() {
        val json = """
            [
              {"occurredAt": "2026-11-13T12:00:00Z", "note": "a"},
              {"occurredAt": "2026-11-12T12:00:00Z"}
            ]
        """.trimIndent()

        val imported = EventImporter.parse(json, zone)

        assertEquals(2, imported.events.size)
        assertEquals(0, imported.invalid)
        assertEquals("a", imported.events.first().note)
    }

    @Test
    fun `rejects newer format version with readable message`() {
        val json = """{"formatVersion": 2, "events": []}"""

        val error = assertThrows(EventImportException::class.java) {
            EventImporter.parse(json, zone)
        }

        assertTrue(error.message?.contains("formatVersion=2") == true)
    }

    @Test
    fun `json counts invalid events but imports valid ones`() {
        val json = """
            {"formatVersion": 1, "events": [
              {"occurredAt": "not-a-time"},
              {"occurredAt": "2026-11-13T12:00:00Z"}
            ]}
        """.trimIndent()

        val imported = EventImporter.parse(json, zone)

        assertEquals(1, imported.invalid)
        assertEquals(1, imported.events.size)
        val event = imported.events.single()
        assertEquals(Instant.parse("2026-11-13T12:00:00Z"), event.occurredAt)
        assertEquals(event.occurredAt, event.createdAt)
    }

    @Test
    fun `header only csv yields no events`() {
        val imported = EventImporter.parse("id,发生时间,记录时间,备注\n", zone)

        assertEquals(0, imported.events.size)
        assertEquals(0, imported.invalid)
        assertEquals("CSV", imported.format)
    }

    @Test
    fun `invalid csv rows are counted`() {
        val csv = buildString {
            appendLine("id,发生时间,记录时间,备注")
            appendLine("1,不是时间,2026-11-13 20:00:00,")
            appendLine("2,2026-11-13 19:00:00,2026-11-13 19:00:01,ok")
        }

        val imported = EventImporter.parse(csv, zone)

        assertEquals(1, imported.invalid)
        assertEquals(1, imported.events.size)
        assertEquals("ok", imported.events.single().note)
        assertEquals(Instant.parse("2026-11-13T11:00:00Z"), imported.events.single().occurredAt)
    }

    @Test
    fun `empty file throws readable error`() {
        val error = assertThrows(EventImportException::class.java) {
            EventImporter.parse("  \n  ", zone)
        }

        assertTrue(error.message?.contains("空") == true)
    }
}
