package com.skhealth.guardian.shared

/**
 * Coalesces DATA_STALE, WATCH_DISCONNECTED and SENSOR_FAILURE only when they
 * describe the same uninterrupted technical incident. A new valid reading
 * breaks the relationship, so later independent failures are never hidden.
 */
object TechnicalConnectivityCoalescingPolicy {
    fun suppressDataStale(
        lastReadingMs: Long,
        lastHeartbeatMs: Long,
        disconnectAlertedForHeartbeatMs: Long,
        sensorFailureAlertedForReadingMs: Long = 0L
    ): Boolean {
        if (lastReadingMs <= 0L) return false
        val sameSensorIncident = sensorFailureAlertedForReadingMs == lastReadingMs
        val sameDisconnectIncident = lastHeartbeatMs > 0L &&
            disconnectAlertedForHeartbeatMs == lastHeartbeatMs &&
            lastReadingMs <= lastHeartbeatMs
        return sameSensorIncident || sameDisconnectIncident
    }

    fun suppressWatchDisconnected(
        lastReadingMs: Long,
        lastHeartbeatMs: Long,
        staleAlertedForReadingMs: Long,
        sensorFailureAlertedForReadingMs: Long = 0L
    ): Boolean {
        if (lastReadingMs <= 0L || lastHeartbeatMs <= 0L) return false
        val sameStaleIncident = staleAlertedForReadingMs == lastReadingMs &&
            lastReadingMs <= lastHeartbeatMs
        val sameSensorIncident = sensorFailureAlertedForReadingMs == lastReadingMs &&
            lastReadingMs <= lastHeartbeatMs
        return sameStaleIncident || sameSensorIncident
    }

    fun suppressSensorFailure(
        lastReadingMs: Long,
        lastHeartbeatMs: Long,
        staleAlertedForReadingMs: Long,
        disconnectAlertedForHeartbeatMs: Long
    ): Boolean {
        if (lastReadingMs <= 0L) return false
        val sameStaleIncident = staleAlertedForReadingMs == lastReadingMs
        val sameDisconnectIncident = lastHeartbeatMs > 0L &&
            disconnectAlertedForHeartbeatMs == lastHeartbeatMs &&
            lastReadingMs <= lastHeartbeatMs
        return sameStaleIncident || sameDisconnectIncident
    }
}
