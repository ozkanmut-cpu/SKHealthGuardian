package com.skhealth.guardian.mobile

import android.content.Context

/**
 * Persistent completion marker for escalation delivery.
 *
 * The marker is intentionally written only after remote delivery attempts finish. This favors
 * retrying after a process crash over silently losing an escalation. Sequential duplicate
 * broadcasts are suppressed across process recreation/reboot once completion is recorded.
 */
object EscalationDeliveryState {
    private const val PREF = "escalation_delivery_state"
    private const val PREFIX = "done_"
    private const val MAX_ENTRIES = 512

    fun isDelivered(context: Context, identity: String): Boolean = synchronized(this) {
        if (identity.isBlank()) return@synchronized false
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .contains(PREFIX + identity)
    }

    fun markDelivered(context: Context, identity: String, completedAtMs: Long = System.currentTimeMillis()) = synchronized(this) {
        if (identity.isBlank()) return@synchronized
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().putLong(PREFIX + identity, completedAtMs).commit()

        val completed = prefs.all
            .filterKeys { it.startsWith(PREFIX) }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }
        if (completed.size > MAX_ENTRIES) {
            val removeCount = completed.size - MAX_ENTRIES
            val editor = prefs.edit()
            completed.sortedBy { it.second }.take(removeCount).forEach { editor.remove(it.first) }
            editor.commit()
        }
    }

    internal fun clearForQa(context: Context, identity: String) = synchronized(this) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(PREFIX + identity).commit()
    }

    fun identity(alertId: String?, alertTs: Long): String =
        if (!alertId.isNullOrBlank()) "id:$alertId" else if (alertTs > 0L) "legacy:$alertTs" else ""
}
