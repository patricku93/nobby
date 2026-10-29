package com.nobby.ring.notifications

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Singleton

interface ScheduleChangeListener {
    suspend fun onScheduleChanged()
}

@Singleton
class ScheduleChangeNotifier @Inject constructor(
    private val listeners: Set<@JvmSuppressWildcards ScheduleChangeListener>,
) {
    suspend fun notifyChanged() {
        listeners.forEach { l ->
            runCatching { l.onScheduleChanged() }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("NoBby", "schedule listener failed", e)
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleChangeModule {
    @Multibinds abstract fun listeners(): Set<ScheduleChangeListener>
}
