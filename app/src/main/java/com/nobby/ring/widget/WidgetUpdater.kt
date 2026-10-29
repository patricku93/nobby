package com.nobby.ring.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) : ScheduleChangeListener {
    override suspend fun onScheduleChanged() = updateAll()
    suspend fun updateAll() = RingWidget().updateAll(context)
}
