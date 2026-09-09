package com.skhealth.guardian.shared

/**
 * Decides whether a remote delivery action that belongs to an alarm may still run.
 * Non-alarm traffic (alertTs <= 0) is always allowed.
 */
object RemoteDeliveryGate {
    fun shouldDeliver(lastAcknowledgedAt: Long, alertTs: Long): Boolean =
        alertTs <= 0L || lastAcknowledgedAt < alertTs
}
