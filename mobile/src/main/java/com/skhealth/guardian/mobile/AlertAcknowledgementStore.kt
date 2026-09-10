package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.AlertIdentity

object AlertAcknowledgementStore {
    private const val PREF = "alert_ack"
    private const val KEY_LAST_ACK = "last_ack"
    private const val KEY_ACK_IDS = "ack_ids"
    private const val MAX_ACK_IDS = 256

    /**
     * Exact-ID acknowledgement used by the current alarm/escalation flow.
     * Returns true only after the acknowledgement is durably committed.
     */
    fun acknowledge(context: Context, alertId: String): Boolean {
        if (!AlertIdentity.isValid(alertId)) return false
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val protected = EscalationScheduler.pendingExactAlertIds(context)
        return BoundedIdStore.add(prefs, KEY_ACK_IDS, alertId, MAX_ACK_IDS, protected)
    }

    fun isAcknowledged(context: Context, alertId: String): Boolean {
        if (!AlertIdentity.isValid(alertId)) return false
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return BoundedIdStore.contains(prefs, KEY_ACK_IDS, alertId)
    }

    /**
     * Legacy timestamp watermark kept for backward compatibility with already-scheduled alarms
     * and older Wear payloads during migration. Returns true only when the watermark is already
     * at/after the requested timestamp or the new value is durably committed.
     */
    fun acknowledge(context: Context, alertTimestampMs: Long = System.currentTimeMillis()): Boolean {
        if (alertTimestampMs <= 0L) return false
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        synchronized(this) {
            val current = prefs.getLong(KEY_LAST_ACK, 0L)
            if (alertTimestampMs <= current) return true
            // ACK is a safety boundary: persist it before returning so a process death cannot
            // resurrect an already-silenced legacy escalation.
            return prefs.edit().putLong(KEY_LAST_ACK, alertTimestampMs).commit()
        }
    }

    fun lastAcknowledgedAt(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_LAST_ACK, 0L)
}
