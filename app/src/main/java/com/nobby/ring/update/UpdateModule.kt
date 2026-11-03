package com.nobby.ring.update

import com.nobby.ring.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds abstract fun announcer(impl: UpdateNotifier): UpdateAnnouncer

    companion object {
        @Provides @Singleton
        fun fetcher(): ReleaseFetcher = HttpUrlConnectionFetcher()

        @Provides @Singleton
        fun source(fetcher: ReleaseFetcher): ReleaseSource = UpdateChecker(fetcher, BuildConfig.VERSION_NAME)
    }
}
