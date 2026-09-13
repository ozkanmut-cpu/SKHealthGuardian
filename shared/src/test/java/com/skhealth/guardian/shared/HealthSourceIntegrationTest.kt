package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-policy integration scenarios for the two SpO2 sources.
 * These tests intentionally stay in the shared module so CI can exercise
 * source priority + PC60 staged alarm rules without Android dependencies.
 */
class HealthSourceIntegrationTest {
    private fun pc60(
        ts: Long,
        spo2: Int,
        valid: Boolean = true,
        pulseSearching: Boolean = false
    ) = Pc60Sample(
        timestampMs = ts,
        spo2 = if (valid) spo2 else 0,
        pulseRate = if (valid) 78 else 0,
        perfusionIndex = if (valid) 4.0 else 0.0,
        probeOff = !valid,
        pulseSearching = pulseSearching
    )

    private fun stagedPolicy() = Pc60AlarmPolicy(
        immediateThreshold = 75,
        intermediateThreshold = 85,
        fullRecoveryThreshold = 90,
        earlyWindowMs = 3 * 60_000L,
        totalWindowMs = 5 * 60_000L,
        recoveryStableMs = 15_000L
    )

    @Test
    fun freshPc60SuppressesWatchThenWatchResumesAfterFreshnessWindow() {
        val packetAt = 100_000L

        assertTrue(
            SourcePriorityPolicy.isPc60Authoritative(
                nowMs = packetAt + 5_000L,
                lastPacketAt = packetAt,
                spo2 = 84,
                probeOff = false,
                pulseSearching = false
            )
        )

        assertFalse(
            SourcePriorityPolicy.isPc60Authoritative(
                nowMs = packetAt + 10_001L,
                lastPacketAt = packetAt,
                spo2 = 84,
                probeOff = false,
                pulseSearching = false
            )
        )
    }

    @Test
    fun pc60Below85AlarmsAtThreeMinutesWhileWatchRemainsSecondary() {
        val policy = stagedPolicy()

        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(1_000L, 84)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(120_000L, 83)))
        assertEquals(Pc60Decision.ALARM_EARLY, policy.evaluate(pc60(181_000L, 82)))
    }

    @Test
    fun stableRecoveryAt90CancelsPendingPc60AlarmAndLaterLowStartsNewWindow() {
        val policy = stagedPolicy()

        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(0L, 84)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(60_000L, 86)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(100_000L, 90)))
        assertEquals(Pc60Decision.RECOVERED, policy.evaluate(pc60(115_000L, 91)))

        // A new low episode must get a fresh staged observation window.
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(130_000L, 84)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(309_999L, 84)))
        assertEquals(Pc60Decision.ALARM_EARLY, policy.evaluate(pc60(310_000L, 84)))
    }

    @Test
    fun probeOffInvalidatesPc60AuthorityAndResetsPendingLowSession() {
        val policy = stagedPolicy()

        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(0L, 82)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(60_000L, 0, valid = false)))

        assertFalse(
            SourcePriorityPolicy.isPc60Authoritative(
                nowMs = 60_000L,
                lastPacketAt = 60_000L,
                spo2 = 0,
                probeOff = true,
                pulseSearching = false
            )
        )

        // The previous minute must not count after probe-off.
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(120_000L, 82)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(299_999L, 82)))
        assertEquals(Pc60Decision.ALARM_EARLY, policy.evaluate(pc60(300_000L, 82)))
    }

    @Test
    fun pulseSearchingAlsoForcesImmediateWatchFallback() {
        assertFalse(
            SourcePriorityPolicy.isPc60Authoritative(
                nowMs = 200_000L,
                lastPacketAt = 199_500L,
                spo2 = 90,
                probeOff = false,
                pulseSearching = true
            )
        )
    }
}
