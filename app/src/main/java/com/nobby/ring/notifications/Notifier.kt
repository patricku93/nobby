package com.nobby.ring.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nobby.ring.MainActivity
import com.nobby.ring.R
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.ui.components.formatDateTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun showReminder(kind: EventKind, plannedAt: LocalDateTime, importance: Importance, eventId: String) {
        val n = NotificationCompat.Builder(context, Channels.idFor(importance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_reminder_title, kindLabel(kind)))
            .setContentText(context.getString(R.string.notif_reminder_body, formatDateTime(plannedAt)))
            .setPriority(priorityFor(importance))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notify(eventId.hashCode(), n)
    }

    fun showOverdue(kind: EventKind, plannedAt: LocalDateTime, eventId: String, safetyBreached: Boolean) {
        // REMOVE/SWAP overdue means the ring stays in too long; INSERT overdue means the break runs too long.
        val safety = if (kind == EventKind.INSERT) R.string.notif_overdue_safety_pause else R.string.notif_overdue_safety_wear
        val text = buildString {
            append(context.getString(R.string.notif_overdue_body, formatDateTime(plannedAt)))
            if (safetyBreached) append("\n").append(context.getString(safety))
        }
        val builder = NotificationCompat.Builder(context, Channels.URGENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_overdue_title, kindLabel(kind)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_done), action(ActionReceiver.ACTION_DONE, eventId))
            .addAction(0, context.getString(R.string.home_snooze), action(ActionReceiver.ACTION_SNOOZE, eventId))
        // subText stays visible while the notification is collapsed, unlike the second line of the big text.
        if (safetyBreached) builder.setSubText(context.getString(R.string.notif_overdue_safety_short))
        notify(OVERDUE_ID, builder.build())
    }

    fun cancelOverdue() = NotificationManagerCompat.from(context).cancel(OVERDUE_ID)

    private fun notify(id: Int, n: Notification) {
        // notify() throws SecurityException when POST_NOTIFICATIONS is denied; that must not crash a receiver.
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(action: String, eventId: String): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_EVENT_ID, eventId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun kindLabel(kind: EventKind): String = context.getString(
        when (kind) {
            EventKind.REMOVE -> R.string.event_remove
            EventKind.INSERT -> R.string.event_insert
            EventKind.SWAP -> R.string.event_swap
        },
    )

    private fun priorityFor(i: Importance) = when (i) {
        Importance.LOW -> NotificationCompat.PRIORITY_LOW
        Importance.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
        Importance.HIGH -> NotificationCompat.PRIORITY_HIGH
        Importance.URGENT -> NotificationCompat.PRIORITY_MAX
    }

    companion object { const val OVERDUE_ID = 1 }
}
