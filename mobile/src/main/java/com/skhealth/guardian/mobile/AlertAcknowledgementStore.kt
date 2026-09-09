package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlertIdentity

object AlertAcknowledgementStore {
    private const val PREF = "alert_ack"
    private const val KEY_LAST_ACK = "last_ack"
    private const val KEY_ACK_IDS = "ack_ids"
    private const val MAX_ACK_IDS = 256

    /** Exact-ID acknowledgement used by current alarm/escalation flow. */
    fun acknowledge(context: Context, alertId: String) {
        if (!AlertIdentity.isValid(alertId)) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        synchronized(this) {
            val ordered = prefs.getString(KEY_ACK_IDS, "")
                .orEmpty().lines().filter { it.isNotBlank() && it != alertId }.toMutableList()
            ordered += alertId
            prefs.edit().putString(KEY_ACK_IDS, ordered.takeLast(MAX_ACK_IDS).joinToString("\n")).apply()
        }
    }

    fun isAcknowledged(context: Context, alertId: String): Boolean {
        if (!AlertIdentity.isValid(alertId)) return false
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ACK_IDS, "").orEmpty()
        return raw.lineSequence().any { it == alertId }
    }

    /**
     * Legacy timestamp watermark kept for backward compatibility with already-scheduled alarms
     * and older Wear payloads during migration.
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
