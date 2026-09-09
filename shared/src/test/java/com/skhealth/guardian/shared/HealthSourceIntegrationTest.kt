package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-policy integration scenarios for the two SpO2 sources.
 * These tests intentionally stay in the shared module so CI can exercise
 * source priority + PC60 persistence rules without Android dependencies.
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
    fun pc60LowPersistenceAlarmsAtTwoMinutesWhileWatchRemainsSecondary() {
        val policy = Pc60AlarmPolicy(
            alarmThreshold = 85,
            confirmDelayMs = 120_000L,
            recoveryThreshold = 85,
            recoveryStableMs = 10_000L
        )

        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(1_000L, 84)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(60_000L, 83)))
        assertEquals(Pc60Decision.ALARM, policy.evaluate(pc60(121_000L, 82)))
    }

    @Test
    fun stableRecoveryCancelsPendingPc60AlarmAndLaterLowStartsNewWindow() {
        val policy = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)

        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(0L, 84)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(30_000L, 86)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(39_999L, 87)))
        assertEquals(Pc60Decision.RECOVERED, policy.evaluate(pc60(40_000L, 88)))

        // A new low episode must get a fresh full confirmation window.
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(50_000L, 84)))
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(169_999L, 84)))
        assertEquals(Pc60Decision.ALARM, policy.evaluate(pc60(170_000L, 84)))
    }

    @Test
    fun probeOffInvalidatesPc60AuthorityAndResetsPendingLowSession() {
        val policy = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)

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

        // The previous 60 seconds must not count after probe-off.
        assertEquals(Pc60Decision.NONE, policy.evaluate(pc60(120_000L, 82)))
        assertEquals(Pc60Decision.ALARM, policy.evaluate(pc60(240_000L, 82)))
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
