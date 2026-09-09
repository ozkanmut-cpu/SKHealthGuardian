package com.skhealth.guardian.mobile

import android.content.Context

object AlertAcknowledgementStore {
    private const val PREF = "alert_ack"
    private const val KEY_LAST_ACK = "last_ack"

    /**
     * ACK timestamps are monotonic. A delayed/replayed acknowledgement for an older alarm
     * must never move the stored watermark backwards and affect escalation decisions.
     */
    fun acknowledge(context: Context, alertTimestampMs: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        synchronized(this) {
            val current = prefs.getLong(KEY_LAST_ACK, 0L)
            if (alertTimestampMs > current) {
                prefs.edit().putLong(KEY_LAST_ACK, alertTimestampMs).apply()
            }
        }
    }

    fun lastAcknowledgedAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST_ACK, 0L)
}
