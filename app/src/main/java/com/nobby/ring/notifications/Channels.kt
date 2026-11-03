package com.nobby.ring.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.nobby.ring.R
import com.nobby.ring.domain.model.Importance

object Channels {
    const val LOW = "nobby_low"
    const val NORMAL = "nobby_normal"
    const val HIGH = "nobby_high"
    const val URGENT = "nobby_urgent"
    const val UPDATES = "nobby_updates"

    fun idFor(importance: Importance): String = when (importance) {
        Importance.LOW -> LOW
        Importance.NORMAL -> NORMAL
        Importance.HIGH -> HIGH
        Importance.URGENT -> URGENT
    }

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(LOW, context.getString(R.string.channel_low), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(NORMAL, context.getString(R.string.channel_normal), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(
            NotificationChannel(HIGH, context.getString(R.string.channel_high), NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
        )
        nm.createNotificationChannel(
            NotificationChannel(URGENT, context.getString(R.string.channel_urgent), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setBypassDnd(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                )
            },
        )
    }
}
