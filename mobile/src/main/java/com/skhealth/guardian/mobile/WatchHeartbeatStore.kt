package com.skhealth.guardian.mobile

import android.content.Context

object WatchHeartbeatStore {
    private const val PREF = "watch_heartbeat"
    fun mark(context: Context, batteryPct: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong("timestamp", System.currentTimeMillis())
            .putInt("battery", batteryPct)
            .apply()
    }
    fun timestamp(context: Context): Long = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("timestamp", 0L)
    fun battery(context: Context): Int = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("battery", -1)
}
