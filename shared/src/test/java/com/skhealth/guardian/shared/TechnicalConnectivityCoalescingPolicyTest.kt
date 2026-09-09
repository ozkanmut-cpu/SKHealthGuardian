package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalConnectivityCoalescingPolicyTest {
    @Test
    fun dataStaleIsSuppressedWhenSameDisconnectIncidentAlreadyAlerted() {
        assertTrue(
            TechnicalConnectivityCoalescingPolicy.suppressDataStale(
                lastReadingMs = 900L,
                lastHeartbeatMs = 1_000L,
                disconnectAlertedForHeartbeatMs = 1_000L
            )
        )
    }

    @Test
    fun laterValidReadingBreaksDisconnectCoalescing() {
        assertFalse(
            TechnicalConnectivityCoalescingPolicy.suppressDataStale(
                lastReadingMs = 1_100L,
                lastHeartbeatMs = 1_000L,
                disconnectAlertedForHeartbeatMs = 1_000L
            )
        )
    }

    @Test
    fun differentHeartbeatIncidentDoesNotSuppressDataStale() {
        assertFalse(
            TechnicalConnectivityCoalescingPolicy.suppressDataStale(
                lastReadingMs = 900L,
                lastHeartbeatMs = 1_000L,
                disconnectAlertedForHeartbeatMs = 800L
            )
        )
    }

    @Test
    fun disconnectIsSuppressedWhenSameDataStaleIncidentAlreadyAlerted() {
        assertTrue(
            TechnicalConnectivityCoalescingPolicy.suppressWatchDisconnected(
                lastReadingMs = 900L,
                lastHeartbeatMs = 1_000L,
                staleAlertedForReadingMs = 900L
            )
        )
    }

    @Test
    fun invalidAnchorsNeverCoalesce() {
        assertFalse(TechnicalConnectivityCoalescingPolicy.suppressDataStale(0L, 1_000L, 1_000L))
        assertFalse(TechnicalConnectivityCoalescingPolicy.suppressWatchDisconnected(900L, 0L, 900L))
    }
}
