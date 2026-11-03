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
    override suspend fun doWork(): Result = resultFor(syncer.syncNow(), runAttemptCount)

    companion object {
        /** Total runs allowed; runAttemptCount is 0-based. */
        private const val MAX_ATTEMPTS = 3

        /** Maps a sync outcome to the WorkManager result: only failures that waiting can fix are retried. */
        internal fun resultFor(outcome: kotlin.Result<Int>, attempt: Int): Result {
            val error = outcome.exceptionOrNull()
            return when {
                outcome.isSuccess -> Result.success()
                // Calendar deleted, read-only or replaced: settings shows the hint; retrying cannot help until the user picks again.
                error is CalendarUnavailableException -> Result.failure()
                // Permission revoked: retrying cannot help until the user grants it again.
                error is SecurityException -> Result.failure()
                attempt >= MAX_ATTEMPTS - 1 -> Result.failure()
                else -> Result.retry()
            }
        }
    }
}
