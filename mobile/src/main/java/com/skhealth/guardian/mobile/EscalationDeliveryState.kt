package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.ExecutionLeasePolicy

/**
 * Persistent completion + execution lease for escalation delivery.
 *
 * A short durable lease prevents two receiver instances from running the same remote delivery
 * concurrently. The receiver also rearms a recovery alarm for the lease deadline; if the process
 * dies mid-delivery, the next receiver may acquire the expired lease and retry instead of losing
 * the escalation permanently.
 */
object EscalationDeliveryState {
    private const val PREF = "escalation_delivery_state"
    private const val DONE_PREFIX = "done_"
    private const val LEASE_PREFIX = "lease_"
    private const val MAX_ENTRIES = 512
    const val DEFAULT_LEASE_MS = 2 * 60_000L

    fun isDelivered(context: Context, identity: String): Boolean = synchronized(this) {
        if (identity.isBlank()) return@synchronized false
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .contains(DONE_PREFIX + identity)
    }

    /** Returns true only for the receiver instance that owns the current execution lease. */
    fun tryAcquireLease(
        context: Context,
        identity: String,
        nowMs: Long = System.currentTimeMillis(),
        leaseMs: Long = DEFAULT_LEASE_MS
    ): Boolean = synchronized(this) {
        if (identity.isBlank()) return@synchronized false
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val delivered = prefs.contains(DONE_PREFIX + identity)
        val existing = prefs.getLong(LEASE_PREFIX + identity, 0L)
        if (!ExecutionLeasePolicy.canAcquire(delivered, existing, nowMs, leaseMs)) return@synchronized false
        prefs.edit().putLong(LEASE_PREFIX + identity, nowMs).commit()
    }

    fun markDelivered(
        context: Context,
        identity: String,
        completedAtMs: Long = System.currentTimeMillis()
    ) = synchronized(this) {
        if (identity.isBlank()) return@synchronized
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(DONE_PREFIX + identity, completedAtMs)
            .remove(LEASE_PREFIX + identity)
            .commit()

        val completed = prefs.all
            .filterKeys { it.startsWith(DONE_PREFIX) }
            .mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }
        if (completed.size > MAX_ENTRIES) {
            val removeCount = completed.size - MAX_ENTRIES
            val editor = prefs.edit()
            completed.sortedBy { it.second }.take(removeCount).forEach { editor.remove(it.first) }
            editor.commit()
        }
    }

    fun releaseLease(context: Context, identity: String) = synchronized(this) {
        if (identity.isBlank()) return@synchronized
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().remove(LEASE_PREFIX + identity).commit()
    }

    internal fun clearForQa(context: Context, identity: String) = synchronized(this) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(DONE_PREFIX + identity)
            .remove(LEASE_PREFIX + identity)
            .commit()
    }

    fun identity(alertId: String?, alertTs: Long): String =
        if (!alertId.isNullOrBlank()) "id:$alertId" else if (alertTs > 0L) "legacy:$alertTs" else ""
}
