package com.nobby.ring.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds @IntoSet abstract fun scheduler(impl: ReminderScheduler): ScheduleChangeListener
}
