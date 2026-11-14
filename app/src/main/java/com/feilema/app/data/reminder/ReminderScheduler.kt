package com.feilema.app.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.feilema.app.domain.usecase.reminder.ReminderSchedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 用 AlarmManager 安排每天一次的提醒；Receiver 触发后会自动安排下一次。 */
object ReminderScheduler {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context, time: LocalTime) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = ReminderSchedule.nextTrigger(Instant.now(), time, ZoneId.systemDefault())
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toEpochMilli(),
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
