package com.nobby.ring.data.calendar

/** The stored calendar no longer exists or is no longer writable (deleted outside the app, account removed). */
class CalendarUnavailableException(val calendarId: Long) : Exception("Calendar $calendarId is no longer available")
