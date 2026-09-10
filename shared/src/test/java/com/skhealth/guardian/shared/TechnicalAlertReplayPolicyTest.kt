package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalAlertReplayPolicyTest {
    @Test
    fun freshTechnicalAlertWithoutRecoveryIsAccepted() {
        assertTrue(
            TechnicalAlertReplayPolicy.shouldAccept(
                nowMs = 1_000_000L,
                alertTimestampMs = 900_000L,
                lastValidReadingMs = 890_000L,
                maxAgeMs = 600_000L
            )
        )
    }

    @Test
    fun alertOlderThanMaxAgeIsRejected() {
        assertFalse(
            TechnicalAlertReplayPolicy.shouldAccept(
                nowMs = 1_000_001L,
                alertTimestampMs = 400_000L,
                lastValidReadingMs = 0L,
                maxAgeMs = 600_000L
            )
        )
    }

    @Test
    fun validReadingAfterAlertMeansRecoveryAndRejectsReplay() {
        assertFalse(
            TechnicalAlertReplayPolicy.shouldAccept(
                nowMs = 1_000_000L,
                alertTimestampMs = 900_000L,
                lastValidReadingMs = 940_001L,
                maxAgeMs = 600_000L,
                clockSkewAllowanceMs = 40_000L
            )
        )
    }

    @Test
    fun smallClockSkewDoesNotCreateFalseRecovery() {
        assertTrue(
            TechnicalAlertReplayPolicy.shouldAccept(
                nowMs = 1_000_000L,
                alertTimestampMs = 900_000L,
                lastValidReadingMs = 929_999L,
                maxAgeMs = 600_000L,
                clockSkewAllowanceMs = 30_000L
            )
        )
    }

    @Test
    fun futureAlertClockSkewIsAcceptedWhenOtherwiseValid() {
        assertTrue(
            TechnicalAlertReplayPolicy.shouldAccept(
                nowMs = 900_000L,
                alertTimestampMs = 905_000L,
                lastValidReadingMs = 0L,
                maxAgeMs = 600_000L
            )
        )
    }

    @Test
    fun invalidArgumentsAreRejected() {
        assertFalse(TechnicalAlertReplayPolicy.shouldAccept(0L, 1L, 0L, 1L))
        assertFalse(TechnicalAlertReplayPolicy.shouldAccept(1L, 0L, 0L, 1L))
        assertFalse(TechnicalAlertReplayPolicy.shouldAccept(1L, 1L, 0L, 0L))
        assertFalse(TechnicalAlertReplayPolicy.shouldAccept(1L, 1L, 0L, 1L, -1L))
    }
}
