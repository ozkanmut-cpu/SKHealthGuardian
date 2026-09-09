package com.skhealth.guardian.shared

object WatchConnectionPolicy {
    fun shouldAlert(
        nowMs: Long,
        lastHeartbeatMs: Long,
        timeoutMs: Long,
        lastAlertedForHeartbeatMs: Long
    ): Boolean {
        if (nowMs <= 0L || lastHeartbeatMs <= 0L || timeoutMs <= 0L) return false
        if (nowMs < lastHeartbeatMs) return false
        if (nowMs - lastHeartbeatMs < timeoutMs) return false
        return lastAlertedForHeartbeatMs != lastHeartbeatMs
    }
}
