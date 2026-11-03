package com.nobby.ring.notifications

import android.content.Context
import android.graphics.Bitmap
import com.nobby.ring.domain.model.Palette
import com.nobby.ring.ui.mascot.MascotBitmaps
import com.nobby.ring.ui.mascot.MascotMood
import com.nobby.ring.ui.mascot.mascotColors
import java.util.concurrent.ConcurrentHashMap

/** Flat NoBby faces for the notification large icon: per preset (light colours) x mood, rendered once and cached. */
object NotificationIcons {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun large(context: Context, palette: Palette, mood: MascotMood): Bitmap {
        val px = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        return cache.getOrPut("${palette.name}-${mood.name}-$px") { MascotBitmaps.face(mood, palette.mascotColors(dark = false), px) }
    }
}
