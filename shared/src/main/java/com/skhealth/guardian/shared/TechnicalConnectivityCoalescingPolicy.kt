package com.skhealth.guardian.shared

/**
 * Coalesces DATA_STALE and WATCH_DISCONNECTED only when they describe the same
 * uninterrupted connectivity loss. A valid reading after the heartbeat breaks
 * the relationship, so a later data-stale condition is never hidden behind an
 * older watch-disconnect alarm.
 */
object TechnicalConnectivityCoalescingPolicy {
    fun suppressDataStale(
        lastReadingMs: Long,
        lastHeartbeatMs: Long,
        disconnectAlertedForHeartbeatMs: Long
    ): Boolean {
        if (lastReadingMs <= 0L || lastHeartbeatMs <= 0L) return false
        return disconnectAlertedForHeartbeatMs == lastHeartbeatMs &&
            lastReadingMs <= lastHeartbeatMs
    }

    fun suppressWatchDisconnected(
        lastReadingMs: Long,
        lastHeartbeatMs: Long,
        staleAlertedForReadingMs: Long
    ): Boolean {
        if (lastReadingMs <= 0L || lastHeartbeatMs <= 0L) return false
        return staleAlertedForReadingMs == lastReadingMs &&
            lastReadingMs <= lastHeartbeatMs
    }
}
