package com.skhealth.guardian.shared

object WatchdogPolicy {
    fun shouldAlert(
        nowMs: Long,
        lastReadingMs: Long,
        staleAfterMs: Long,
        lastAlertedForReadingMs: Long
    ): Boolean {
        if (lastReadingMs <= 0L || staleAfterMs <= 0L) return false
        if (nowMs < lastReadingMs) return false
        if (nowMs - lastReadingMs <= staleAfterMs) return false
        return lastAlertedForReadingMs < lastReadingMs
    }
}
