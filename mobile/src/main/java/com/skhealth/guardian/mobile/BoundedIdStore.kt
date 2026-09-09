package com.skhealth.guardian.mobile

import android.content.SharedPreferences

/**
 * Small durable bounded set stored as newline-delimited IDs.
 * Writes are serialized and committed before returning because callers use this
 * for acknowledgement / delivery state that must survive an immediate process death.
 */
object BoundedIdStore {
    @Synchronized
    fun add(prefs: SharedPreferences, key: String, id: String, maxEntries: Int): Boolean {
        if (id.isBlank() || maxEntries <= 0) return false
        val ordered = prefs.getString(key, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() && it != id }
            .toMutableList()
        ordered += id
        return prefs.edit()
            .putString(key, ordered.takeLast(maxEntries).joinToString("\n"))
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
