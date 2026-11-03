package com.nobby.ring.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nobby.ring.R
import com.nobby.ring.notifications.Channels
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One quiet notification for a new version; tapping it opens the release page. Same id, so it never stacks. */
class UpdateNotifier @Inject constructor(@ApplicationContext private val context: Context) : UpdateAnnouncer {
    override fun announce(offer: UpdateResult.Available) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(offer.pageUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Channels.UPDATES)
            .setSmallIcon(R.drawable.ic_stat_nobby)
            .setContentTitle(context.getString(R.string.update_notif_title))
            .setContentText(context.getString(R.string.update_notif_body, offer.version))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // notify() throws SecurityException when POST_NOTIFICATIONS is denied; the coordinator logs and carries on.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    companion object { const val NOTIFICATION_ID = 2 }
}
