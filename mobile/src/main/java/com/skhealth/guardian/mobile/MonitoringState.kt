package com.skhealth.guardian.mobile

import android.content.Context

object MonitoringState {
    private const val PREF = "monitoring"
    fun markReading(context: Context, ts: Long) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong("last_reading", ts).apply()
    }
    fun lastReading(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("last_reading", 0)
}
