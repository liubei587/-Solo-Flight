package com.feilema.app.domain.usecase.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `uses today when reminder is later today`() {
        val now = Instant.parse("2026-11-13T10:00:00Z") // 本地 18:00

        val next = ReminderSchedule.nextTrigger(now, LocalTime.of(20, 0), zone)

        assertEquals(Instant.parse("2026-11-13T12:00:00Z"), next)
    }

    @Test
    fun `rolls to tomorrow when reminder already passed`() {
        val now = Instant.parse("2026-11-13T14:00:00Z") // 本地 22:00

        val next = ReminderSchedule.nextTrigger(now, LocalTime.of(20, 0), zone)

        assertEquals(Instant.parse("2026-11-14T12:00:00Z"), next)
    }

    @Test
    fun `rolls to tomorrow when now equals reminder time`() {
        val now = Instant.parse("2026-11-13T12:00:00Z") // 本地正好 20:00

        val next = ReminderSchedule.nextTrigger(now, LocalTime.of(20, 0), zone)

        assertEquals(Instant.parse("2026-11-14T12:00:00Z"), next)
    }

    @Test
    fun `handles midnight reminder`() {
        val now = Instant.parse("2026-11-13T15:30:00Z") // 本地 23:30

        val next = ReminderSchedule.nextTrigger(now, LocalTime.MIDNIGHT, zone)

        assertEquals(Instant.parse("2026-11-13T16:00:00Z"), next)
    }
}
