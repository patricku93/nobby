package com.nobby.ring.di

import javax.inject.Qualifier

/**
 * A process-wide [kotlinx.coroutines.CoroutineScope] for work that must outlive the screen that started it
 * (schedule writes followed by re-planning). Never cancelled.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
