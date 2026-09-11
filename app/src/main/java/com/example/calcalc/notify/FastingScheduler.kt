package com.example.calcalc.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.calcalc.data.model.ActiveFast
import com.example.calcalc.data.model.FastingConfig
import com.example.calcalc.data.model.FastingMode
import com.example.calcalc.domain.FastingAlarm
import com.example.calcalc.domain.FastingAlarms

/**
 * Turns the fasting settings into AlarmManager alarms.
 *
 * The settings are mirrored into SharedPreferences because alarms outlive the process: a
 * broadcast receiver waking at 19:45 (or after a reboot) has to know the schedule without
 * signing in to Firestore first.
 */
object FastingScheduler {

    private const val PREFS = "fasting_schedule"
    private const val KEY_MODE = "mode"
    private const val KEY_DAILY_START = "dailyStartMinute"
    private const val KEY_DAILY_END = "dailyEndMinute"
    private const val KEY_ACTIVE_STARTED = "activeStartedAt"
    private const val KEY_ACTIVE_END = "activePlannedEndAt"

    private const val REQUEST_CODE_BASE = 7200
    private const val TAG = "FastingScheduler"

    const val EXTRA_KIND = "kind"

    val LEAD_MINUTES: Long = FastingAlarms.DEFAULT_LEAD.toMinutes()

    /** Called whenever the fasting config or the running fast changes. */
    fun sync(context: Context, config: FastingConfig, active: ActiveFast?) {
        cache(context, config, active)
        schedule(context, config, active)
    }

    /** Re-arms alarms from the mirrored settings, after a reboot or after one has fired. */
    fun rescheduleFromCache(context: Context) {
        val (config, active) = readCache(context)
        schedule(context, config, active)
    }

    private fun schedule(context: Context, config: FastingConfig, active: ActiveFast?) {
        val alarms = FastingAlarms.next(config, active)
        val scheduled = alarms.associateBy { it.kind }

        FastingAlarm.Kind.entries.forEach { kind ->
            val alarm = scheduled[kind]
            if (alarm == null) cancel(context, kind) else set(context, alarm)
        }
    }

    private fun set(context: Context, alarm: FastingAlarm) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context, alarm.kind)
        // Exact alarms need a permission the user has to grant by hand, and a fifteen-minute
        // heads-up does not need second precision — so fall back rather than nag.
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atEpochMillis, pendingIntent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atEpochMillis, pendingIntent)
            }
        }.onFailure { Log.w(TAG, "Couldn't schedule ${alarm.kind}", it) }
    }

    private fun cancel(context: Context, kind: FastingAlarm.Kind) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, kind))
    }

    private fun pendingIntent(context: Context, kind: FastingAlarm.Kind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + kind.ordinal,
            Intent(context, FastingAlarmReceiver::class.java)
                .setAction(FastingAlarmReceiver.ACTION_FASTING_ALARM)
                .putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cache(context: Context, config: FastingConfig, active: ActiveFast?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_MODE, config.mode.name)
            putInt(KEY_DAILY_START, config.dailyStartMinute)
            putInt(KEY_DAILY_END, config.dailyEndMinute)
            putLong(KEY_ACTIVE_STARTED, active?.startedAt ?: 0L)
            putLong(KEY_ACTIVE_END, active?.plannedEndAt ?: 0L)
        }.apply()
    }

    private fun readCache(context: Context): Pair<FastingConfig, ActiveFast?> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val mode = runCatching { FastingMode.valueOf(prefs.getString(KEY_MODE, null).orEmpty()) }
            .getOrDefault(FastingMode.OFF)
        val config = FastingConfig(
            mode = mode,
            dailyStartMinute = prefs.getInt(KEY_DAILY_START, FastingConfig().dailyStartMinute),
            dailyEndMinute = prefs.getInt(KEY_DAILY_END, FastingConfig().dailyEndMinute),
        )
        val active = ActiveFast(
            startedAt = prefs.getLong(KEY_ACTIVE_STARTED, 0L),
            plannedEndAt = prefs.getLong(KEY_ACTIVE_END, 0L),
        ).takeIf { it.isSet }
        return config to active
    }
}
