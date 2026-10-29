package com.nobby.ring.data.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: CalendarSyncer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (syncer.syncNow().isSuccess) Result.success() else Result.retry()
}
