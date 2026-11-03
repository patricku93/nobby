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
    override suspend fun doWork(): Result {
        val outcome = syncer.syncNow()
        return when {
            outcome.isSuccess -> Result.success()
            // Calendar deleted: settings shows the hint, retrying cannot help until the user picks another one.
            outcome.exceptionOrNull() is CalendarUnavailableException -> Result.failure()
            // Permission revoked: retrying cannot help until the user grants it again.
            outcome.exceptionOrNull() is SecurityException -> Result.failure()
            runAttemptCount >= MAX_ATTEMPTS -> Result.failure()
            else -> Result.retry()
        }
    }

    private companion object { const val MAX_ATTEMPTS = 3 }
}
