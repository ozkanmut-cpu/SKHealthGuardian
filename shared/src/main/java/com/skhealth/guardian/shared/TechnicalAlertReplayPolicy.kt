package com.skhealth.guardian.shared

object TechnicalAlertReplayPolicy {
    /**
     * A queued technical alert must not resurrect after recovery. We accept it only
     * while it is reasonably fresh and no valid reading newer than the alert (plus
     * a small clock-skew allowance) has already been observed by the phone.
     */
    fun shouldAccept(
        nowMs: Long,
        alertTimestampMs: Long,
        lastValidReadingMs: Long,
        maxAgeMs: Long,
        clockSkewAllowanceMs: Long = 30_000L
    ): Boolean {
        if (nowMs <= 0L || alertTimestampMs <= 0L || maxAgeMs <= 0L || clockSkewAllowanceMs < 0L) return false
        val age = (nowMs - alertTimestampMs).coerceAtLeast(0L)
        if (age > maxAgeMs) return false
        if (lastValidReadingMs > 0L && lastValidReadingMs > alertTimestampMs + clockSkewAllowanceMs) return false
        return true
    }
}
