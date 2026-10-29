package com.nobby.ring.widget

import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds @IntoSet abstract fun updater(impl: WidgetUpdater): ScheduleChangeListener
}
