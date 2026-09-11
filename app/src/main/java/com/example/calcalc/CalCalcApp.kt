package com.example.calcalc

import android.app.Application
import com.example.calcalc.notify.FastingNotifications

class CalCalcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Cheap and idempotent; doing it here means the channel exists before any alarm
        // fires, including one armed by a previous install-and-reboot.
        FastingNotifications.ensureChannel(this)
    }
}
