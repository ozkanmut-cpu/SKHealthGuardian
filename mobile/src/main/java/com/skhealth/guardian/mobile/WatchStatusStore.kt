package com.skhealth.guardian.mobile

import android.content.Context

object WatchStatusStore {
    private const val PREF = "watch_status"
    fun mark(context: Context, status: String, ts: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("status", status).putLong("ts", ts).apply()
    }
    fun status(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("status", "Bilinmiyor") ?: "Bilinmiyor"
    fun timestamp(context: Context): Long = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("ts", 0)
}
