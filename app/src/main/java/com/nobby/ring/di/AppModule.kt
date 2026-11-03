package com.nobby.ring.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.nobby.ring.data.db.CalendarLinkDao
import com.nobby.ring.data.db.EventLogDao
import com.nobby.ring.data.db.NobbyDatabase
import com.nobby.ring.data.db.NoteDao
import com.nobby.ring.data.db.ReminderRuleDao
import com.nobby.ring.data.prefs.WidgetStyleMigration
import com.nobby.ring.domain.NobbyClock
import com.nobby.ring.domain.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(migrations = listOf(WidgetStyleMigration)) { context.preferencesDataStoreFile("nobby_prefs") }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): NobbyDatabase =
        Room.databaseBuilder(context, NobbyDatabase::class.java, "nobby.db").build()

    @Provides fun eventLogDao(db: NobbyDatabase): EventLogDao = db.eventLogDao()
    @Provides fun noteDao(db: NobbyDatabase): NoteDao = db.noteDao()
    @Provides fun reminderRuleDao(db: NobbyDatabase): ReminderRuleDao = db.reminderRuleDao()
    @Provides fun calendarLinkDao(db: NobbyDatabase): CalendarLinkDao = db.calendarLinkDao()

    @Provides @Singleton fun clock(): NobbyClock = SystemClock()

    /** Single-threaded view of Default: persist + notify launches run in call order. */
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
}
