package com.skhealth.guardian.mobile

import android.content.Context

object MonitoringState {
    private const val PREF = "monitoring"
    private const val KEY_LAST_READING = "last_reading"
    private const val KEY_STALE_ALERTED_FOR = "stale_alerted_for"

    fun markReading(context: Context, ts: Long) {
        if (ts <= 0L) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        synchronized(this) {
            val current = prefs.getLong(KEY_LAST_READING, 0L)
            if (ts > current) prefs.edit().putLong(KEY_LAST_READING, ts).apply()
        }
    }

    fun lastReading(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST_READING, 0L)

    fun markStaleAlertedFor(context: Context, readingTs: Long) {
        if (readingTs <= 0L) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        synchronized(this) {
            val current = prefs.getLong(KEY_STALE_ALERTED_FOR, 0L)
            if (readingTs > current) prefs.edit().putLong(KEY_STALE_ALERTED_FOR, readingTs).apply()
        }
    }

    fun lastStaleAlertedFor(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_STALE_ALERTED_FOR, 0L)
}
