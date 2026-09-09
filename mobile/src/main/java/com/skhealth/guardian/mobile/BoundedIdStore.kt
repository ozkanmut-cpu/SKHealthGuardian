package com.skhealth.guardian.mobile

import android.content.SharedPreferences
import com.skhealth.guardian.shared.BoundedRetentionPolicy

/**
 * Small durable bounded set stored as newline-delimited IDs.
 * Writes are serialized and committed before returning because callers use this
 * for acknowledgement / delivery state that must survive an immediate process death.
 *
 * protectedIds are retained even when they fall outside the normal recent-entry window. This is
 * used by exact alarm ACK storage so an acknowledgement referenced by a still-pending escalation
 * cannot be pruned merely because many newer alarms were acknowledged.
 */
object BoundedIdStore {
    @Synchronized
    fun add(
        prefs: SharedPreferences,
        key: String,
        id: String,
        maxEntries: Int,
        protectedIds: Set<String> = emptySet()
    ): Boolean {
        if (id.isBlank() || maxEntries <= 0) return false
        val current = prefs.getString(key, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        val retained = BoundedRetentionPolicy.retain(current, id, maxEntries, protectedIds)
        return prefs.edit()
            .putString(key, retained.joinToString("\n"))
            .commit()
    }

    fun contains(prefs: SharedPreferences, key: String, id: String): Boolean {
        if (id.isBlank()) return false
        val raw = prefs.getString(key, "").orEmpty()
        return raw.lineSequence().any { it == id }
    }

    fun snapshot(prefs: SharedPreferences, key: String): List<String> =
        prefs.getString(key, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
}
