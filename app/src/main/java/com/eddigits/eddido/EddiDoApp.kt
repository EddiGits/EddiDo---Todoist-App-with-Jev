package com.eddigits.eddido

import android.app.Application
import com.eddigits.eddido.alarm.Notifications
import com.eddigits.eddido.data.TaskRepository

class EddiDoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        TaskRepository.get(this)
    }
}
