package com.feilema.app

import android.app.Application
import com.feilema.app.data.reminder.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FeiLeMaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)
    }
}
