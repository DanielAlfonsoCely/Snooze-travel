package com.snoozetravel.app

import android.app.Application
import com.snoozetravel.app.data.Store
import com.snoozetravel.app.util.Notifications

class SnoozeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifications.createChannels(this)
    }
}
