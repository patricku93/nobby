package com.nobby.ring.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SETTINGS_SCHEDULE = "settings/schedule"
    const val SETTINGS_REMINDERS = "settings/reminders"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_CALENDAR = "settings/calendar"
    const val SETTINGS_CALENDAR_CLEANUP = "settings/calendar/cleanup"
    const val SETTINGS_WIDGET = "settings/widget"
    val topLevel = listOf(HOME, CALENDAR, HISTORY, SETTINGS)
}
