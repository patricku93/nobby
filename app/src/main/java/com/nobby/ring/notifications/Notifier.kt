package com.nobby.ring.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nobby.ring.MainActivity
import com.nobby.ring.R
import com.nobby.ring.data.prefs.ThemeStore
import com.nobby.ring.domain.model.EventKind
import com.nobby.ring.domain.model.Importance
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.components.formatDateTime
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.theme.roles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import javax.inject.Inject

class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeStore: ThemeStore,
) {

    suspend fun showReminder(kind: EventKind, plannedAt: LocalDateTime, importance: Importance, eventId: String) {
        val palette = palette()
        val n = NotificationCompat.Builder(context, Channels.idFor(importance))
            .setSmallIcon(R.drawable.ic_stat_nobby)
            .setLargeIcon(NotificationIcons.large(context, palette, MascotMood.HAPPY))
            .setColor(palette.roles(dark = false).primary.toArgb())
            .setContentTitle(context.getString(R.string.notif_reminder_title, kindLabel(kind)))
            .setContentText(context.getString(R.string.notif_reminder_body, formatDateTime(plannedAt)))
            .setPriority(priorityFor(importance))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        notify(eventId.hashCode(), n)
    }

    suspend fun showOverdue(kind: EventKind, plannedAt: LocalDateTime, eventId: String, safetyBreached: Boolean) {
        val palette = palette()
        // REMOVE/SWAP overdue means the ring stays in too long; INSERT overdue means the break runs too long.
        val safety = if (kind == EventKind.INSERT) R.string.notif_overdue_safety_pause else R.string.notif_overdue_safety_wear
        val body = context.getString(R.string.notif_overdue_body, formatDateTime(plannedAt))
        val text = if (safetyBreached) context.getString(R.string.notif_overdue_body_with_safety, body, context.getString(safety)) else body
        val builder = NotificationCompat.Builder(context, Channels.URGENT)
            .setSmallIcon(R.drawable.ic_stat_nobby)
            .setLargeIcon(NotificationIcons.large(context, palette, MascotMood.WORRIED))
            .setColor(palette.roles(dark = false).primary.toArgb())
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

    /** The active preset; a broken preference file must never stop an alarm, so reading falls back to Berry. */
    private suspend fun palette(): Palette = try {
        themeStore.theme.first().palette
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("NoBby", "reading the theme for a notification failed", e)
        Palette.BERRY
    }

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
