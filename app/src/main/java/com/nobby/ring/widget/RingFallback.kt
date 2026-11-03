package com.nobby.ring.widget

import androidx.compose.runtime.mutableStateOf

/**
 * Process-wide switch to the Classic layout after Glance could not publish a Ring update (for example a Binder
 * transaction that was too large, which happens in AppWidgetManager, outside [RingBitmaps.renderOrNull]).
 * The flag is snapshot state read in the composition, so tripping it recomposes the widget as Classic at once.
 * It stays off until the process restarts; a failure of the Classic layout itself goes to Glance's error UI.
 */
object RingFallback {
    private val failed = mutableStateOf(false)

    /** Whether the last composed widget content used the Ring layout. */
    @Volatile var ringShown: Boolean = false

    val active: Boolean get() = failed.value

    /** True when the failure came from a Ring update and the widget now falls back to Classic. */
    fun onUpdateFailed(ringShown: Boolean): Boolean {
        if (!ringShown || failed.value) return false
        failed.value = true
        return true
    }

    internal fun reset() {
        failed.value = false
        ringShown = false
    }
}
