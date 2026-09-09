package com.skhealth.guardian.mobile

import android.content.Context

object WatchHeartbeatStore {
    private const val PREF = "watch_heartbeat"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_BATTERY = "battery"
    private const val KEY_LAST_DISCONNECT_ALERTED_FOR = "last_disconnect_alerted_for"

    fun mark(context: Context, batteryPct: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .putInt(KEY_BATTERY, batteryPct)
            .apply()
    }

    fun timestamp(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_TIMESTAMP, 0L)

    fun battery(context: Context): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_BATTERY, -1)

    fun lastDisconnectAlertedFor(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST_DISCONNECT_ALERTED_FOR, 0L)

    fun markDisconnectAlertedFor(context: Context, heartbeatTimestampMs: Long) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_DISCONNECT_ALERTED_FOR, heartbeatTimestampMs)
            .commit()
    }
}
