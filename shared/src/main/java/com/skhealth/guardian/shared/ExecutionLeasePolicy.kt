package com.skhealth.guardian.shared

/** Pure policy for crash-recovery execution leases. */
object ExecutionLeasePolicy {
    fun canAcquire(
        delivered: Boolean,
        existingLeaseStartedAt: Long,
        nowMs: Long,
        leaseMs: Long
    ): Boolean {
        if (delivered || nowMs <= 0L || leaseMs <= 0L) return false
        if (existingLeaseStartedAt <= 0L) return true
        // If the wall clock moved backwards, keep the existing lease active rather than allowing
        // an immediate duplicate execution.
        if (existingLeaseStartedAt > nowMs) return false
        return nowMs - existingLeaseStartedAt >= leaseMs
    }
}
