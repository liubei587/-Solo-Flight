package com.feilema.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 收到闹钟后弹通知，并把下一次提醒安排好；用户关掉提醒后不再弹。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = ReminderPreferences.read(context)
        if (!settings.enabled) return

        ReminderNotifications.show(context)
        ReminderScheduler.schedule(context, settings.time)
    }
}
