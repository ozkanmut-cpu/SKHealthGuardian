package com.skhealth.guardian.shared

/** Pure race-safe decision used by Android escalation delivery and stress tests. */
object EscalationGate {
    fun shouldEscalate(lastAcknowledgedAt: Long, alertTimestampMs: Long): Boolean =
        alertTimestampMs > 0L && lastAcknowledgedAt < alertTimestampMs
}
