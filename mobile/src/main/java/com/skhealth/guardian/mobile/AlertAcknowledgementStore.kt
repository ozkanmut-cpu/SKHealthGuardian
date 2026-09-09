package com.skhealth.guardian.mobile

import android.content.Context

object AlertAcknowledgementStore {
    private const val PREF = "alert_ack"
    fun acknowledge(context: Context, alertTimestampMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong("last_ack", alertTimestampMs).apply()
    }
    fun lastAcknowledgedAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("last_ack", 0L)
}
