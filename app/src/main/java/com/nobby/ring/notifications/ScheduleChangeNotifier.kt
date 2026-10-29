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
    suspend fun notifyChanged() = listeners.forEach { it.onScheduleChanged() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ScheduleChangeModule {
    @Multibinds abstract fun listeners(): Set<ScheduleChangeListener>
}
