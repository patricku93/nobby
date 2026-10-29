package com.nobby.ring.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updater: WidgetUpdater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        updater.updateAll()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget_refresh", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
