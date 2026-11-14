package com.feilema.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 重启后 AlarmManager 里的闹钟会丢，这里按设置重新安排一次。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = ReminderPreferences.read(context)
        if (settings.enabled) {
            ReminderScheduler.schedule(context, settings.time)
        }
    }
}
