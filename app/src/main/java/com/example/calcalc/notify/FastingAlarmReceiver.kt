package com.example.calcalc.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calcalc.domain.FastingAlarm

/**
 * Posts a fasting heads-up and immediately arms the next one — a daily window needs a fresh
 * alarm each day, and a reboot clears every pending alarm the system was holding.
 */
class FastingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FASTING_ALARM) {
            val kind = runCatching {
                FastingAlarm.Kind.valueOf(intent.getStringExtra(FastingScheduler.EXTRA_KIND).orEmpty())
            }.getOrNull()
            kind?.let { FastingNotifications.post(context, it, FastingScheduler.LEAD_MINUTES) }
        }
        FastingScheduler.rescheduleFromCache(context)
    }

    companion object {
        const val ACTION_FASTING_ALARM = "com.example.calcalc.FASTING_ALARM"
    }
}
