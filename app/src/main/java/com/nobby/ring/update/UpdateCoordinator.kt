package com.nobby.ring.update

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the one-per-version notification. */
fun interface UpdateAnnouncer {
    fun announce(offer: UpdateResult.Available)
}

/**
 * Runs checks at most once per 24 h (app start and the periodic worker share the gate), keeps the result in
 * [UpdateStore] and announces each new version exactly once.
 */
@Singleton
class UpdateCoordinator internal constructor(
    private val source: ReleaseSource,
    private val store: UpdateStore,
    private val announcer: UpdateAnnouncer,
    private val now: () -> Long,
) {
    @Inject constructor(source: ReleaseSource, store: UpdateStore, announcer: UpdateAnnouncer) :
        this(source, store, announcer, System::currentTimeMillis)

    private val lock = Mutex()

    /** Null when the last successful check is younger than 24 h. */
    suspend fun checkIfDue(): UpdateResult? = lock.withLock {
        val last = store.state.first().lastCheckAt
        if (last != null && now() - last in 0 until MIN_INTERVAL_MS) null else run()
    }

    /** The user asked: ignore the gate. */
    suspend fun checkNow(): UpdateResult = lock.withLock { run() }

    private suspend fun run(): UpdateResult {
        val result = source.check()
        when (result) {
            is UpdateResult.Available -> {
                store.recordCheck(now(), result)
                announceOnce(result)
            }
            UpdateResult.UpToDate -> store.recordCheck(now(), null)
            UpdateResult.Failed -> Unit
        }
        return result
    }

    private suspend fun announceOnce(offer: UpdateResult.Available) {
        val seen = AppVersion.parse(store.state.first().notifiedVersion)
        if (seen == AppVersion.parse(offer.version)) return
        // Mark first: a notification that cannot be posted must not be retried on every check.
        store.markNotified(offer.version)
        try {
            announcer.announce(offer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NoBby", "announcing the update failed", e)
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
