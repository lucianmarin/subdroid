package com.lucianmarin.sub

import android.app.Application
import com.lucianmarin.sub.state.NotificationHelper

class SubreplyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
    }
}
