package com.healthcheck.app

import android.app.Application
import com.healthcheck.app.alarm.Notifications
import com.healthcheck.app.sync.SyncWorker

class HealthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SyncWorker.schedulePeriodic(this)
    }
}
