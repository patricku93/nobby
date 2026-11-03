package com.nobby.ring

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nobby.ring.di.ApplicationScope
import com.nobby.ring.notifications.Channels
import com.nobby.ring.update.UpdateCheckWorker
import com.nobby.ring.update.UpdateCoordinator
import com.nobby.ring.widget.WidgetWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NobbyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var updateCoordinator: UpdateCoordinator
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Channels.create(this)
        WidgetWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        // App start: the coordinator skips this when the last successful check is younger than 24 h.
        appScope.launch { updateCoordinator.checkIfDue() }
    }
}
