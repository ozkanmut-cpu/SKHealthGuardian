package com.skhealth.guardian.shared

/**
 * Accepts timestamps that never move backwards. Equal timestamps are allowed because
 * multiple sensor callbacks may legitimately share the same millisecond.
 */
class MonotonicTimestampGate(initialTimestampMs: Long = 0L) {
    private var lastAcceptedTimestampMs: Long = initialTimestampMs.coerceAtLeast(0L)

    @Synchronized
    fun accept(timestampMs: Long): Boolean {
        if (timestampMs < lastAcceptedTimestampMs) return false
        lastAcceptedTimestampMs = timestampMs
        return true
    }

    @Synchronized
    fun lastAccepted(): Long = lastAcceptedTimestampMs

    @Synchronized
    fun reset(timestampMs: Long = 0L) {
        lastAcceptedTimestampMs = timestampMs.coerceAtLeast(0L)
    }
}
