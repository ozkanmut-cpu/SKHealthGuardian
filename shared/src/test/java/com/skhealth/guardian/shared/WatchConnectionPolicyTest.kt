package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchConnectionPolicyTest {
    private val timeout = 3 * 60_000L

    @Test
    fun noHeartbeatYetDoesNotAlert() {
        assertFalse(WatchConnectionPolicy.shouldAlert(1_000_000L, 0L, timeout, 0L))
    }

    @Test
    fun beforeTimeoutDoesNotAlert() {
        val heartbeat = 1_000_000L
        assertFalse(WatchConnectionPolicy.shouldAlert(heartbeat + timeout - 1L, heartbeat, timeout, 0L))
    }

    @Test
    fun atTimeoutAlertsOnceForThatHeartbeat() {
        val heartbeat = 1_000_000L
        assertTrue(WatchConnectionPolicy.shouldAlert(heartbeat + timeout, heartbeat, timeout, 0L))
        assertFalse(WatchConnectionPolicy.shouldAlert(heartbeat + timeout + 60_000L, heartbeat, timeout, heartbeat))
    }

    @Test
    fun newHeartbeatCreatesNewIncidentAfterAnotherTimeout() {
        val oldHeartbeat = 1_000_000L
        val newHeartbeat = 2_000_000L
        assertTrue(WatchConnectionPolicy.shouldAlert(newHeartbeat + timeout, newHeartbeat, timeout, oldHeartbeat))
    }

    @Test
    fun clockRollbackDoesNotCreateDisconnectAlert() {
        assertFalse(WatchConnectionPolicy.shouldAlert(900_000L, 1_000_000L, timeout, 0L))
    }
}
