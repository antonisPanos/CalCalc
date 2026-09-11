package com.example.calcalc.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.calcalc.MainActivity
import com.example.calcalc.R
import com.example.calcalc.domain.FastingAlarm

/** Builds and posts the fasting heads-ups. Channel setup lives here too, so it is created once. */
object FastingNotifications {

    const val CHANNEL_ID = "fasting"

    private const val NOTIFICATION_ID_BASE = 4100

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Fasting",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminders shortly before a fast starts or ends."
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, kind: FastingAlarm.Kind, leadMinutes: Long) {
        // The alarm still fires without the runtime permission; posting is simply a no-op,
        // and NotificationManagerCompat would throw if we didn't check.
        if (!hasPermission(context)) return
        ensureChannel(context)

        val (title, text) = when (kind) {
            FastingAlarm.Kind.FAST_STARTING_SOON ->
                "Eating window closing" to "Your fast starts in $leadMinutes minutes."
            FastingAlarm.Kind.FAST_ENDING_SOON ->
                "Fast almost over" to "You can eat again in $leadMinutes minutes."
        }

        val openApp = PendingIntent.getActivity(
            context,
            kind.ordinal,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + kind.ordinal, notification)
    }
}
