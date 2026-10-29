package com.nobby.ring.data.calendar

import com.nobby.ring.notifications.ScheduleChangeListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Binds abstract fun source(impl: DeviceCalendarSource): CalendarSource
    @Binds @IntoSet abstract fun listener(impl: CalendarSyncer): ScheduleChangeListener
}
