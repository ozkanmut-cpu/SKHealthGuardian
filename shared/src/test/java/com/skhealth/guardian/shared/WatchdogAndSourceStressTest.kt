package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogAndSourceStressTest {
    @Test
    fun staleIncidentOnlyAlertsOnceAcrossRestartWatermark() {
        val lastReading = 1_000_000L
        val stale = 10 * 60_000L
        val now = lastReading + stale + 1L
        assertTrue(WatchdogPolicy.shouldAlert(now, lastReading, stale, 0L))
        assertFalse(WatchdogPolicy.shouldAlert(now + 60_000L, lastReading, stale, lastReading))
        assertFalse(WatchdogPolicy.shouldAlert(now + 120_000L, lastReading, stale, lastReading + 1L))
        val newerReading = now + 1L
        assertTrue(WatchdogPolicy.shouldAlert(newerReading + stale + 1L, newerReading, stale, lastReading))
    }

    @Test
    fun millionWatchdogBoundaryDecisionsMatchReference() {
        val stale = 900_000L
        var mismatches = 0
        repeat(1_000_000) { i ->
            val last = 1_000_000L + (i % 10_000)
            val age = (i % 1_800_005).toLong()
            val now = last + age
            val alerted = if (i % 7 == 0) last else last - 1
            val expected = last > 0 && now >= last && now - last > stale && alerted < last
            val actual = WatchdogPolicy.shouldAlert(now, last, stale, alerted)
            if (actual != expected) mismatches++
        }
        assertEquals(0, mismatches)
    }

    @Test
    fun futureClockAndInvalidInputsNeverProduceStaleAlarm() {
        assertFalse(WatchdogPolicy.shouldAlert(100L, 200L, 10L, 0L))
        assertFalse(WatchdogPolicy.shouldAlert(100L, 0L, 10L, 0L))
        assertFalse(WatchdogPolicy.shouldAlert(100L, 50L, 0L, 0L))
    }

    @Test
    fun sourcePriorityRapidFlapHasExactBoundaryBehavior() {
        val fresh = SourcePriorityPolicy.DEFAULT_PC60_FRESH_MS
        var mismatches = 0
        repeat(500_000) { i ->
            val lastPacket = 1_000_000L + i
            val age = (i % (fresh.toInt() + 3)).toLong()
            val now = lastPacket + age
            val probeOff = i % 97 == 0
            val searching = i % 89 == 0
            val pi = if (i % 83 == 0) 0.0 else 3.5
            val spo2 = if (i % 79 == 0) 0 else 96
            val hr = if (i % 73 == 0) 0 else 78
            val freshExpected = age in 0..fresh
            val spo2Expected = freshExpected && !probeOff && !searching && spo2 in 1..100 && pi > 0.0
            val hrExpected = freshExpected && !probeOff && !searching && hr in 1..511 && pi > 0.0
            val spo2Actual = SourcePriorityPolicy.isPc60Spo2Authoritative(now, lastPacket, spo2, pi, probeOff, searching)
            val hrActual = SourcePriorityPolicy.isPc60HeartRateAuthoritative(now, lastPacket, hr, pi, probeOff, searching)
            if (spo2Actual != spo2Expected) mismatches++
            if (hrActual != hrExpected) mismatches++
        }
        assertEquals(0, mismatches)
    }

    @Test
    fun stalePc60ImmediatelyFallsBackAtFreshnessBoundary() {
        val last = 5_000_000L
        val fresh = SourcePriorityPolicy.DEFAULT_PC60_FRESH_MS
        assertTrue(SourcePriorityPolicy.isPc60Spo2Authoritative(last + fresh, last, 97, 4.0, false, false))
        assertFalse(SourcePriorityPolicy.isPc60Spo2Authoritative(last + fresh + 1L, last, 97, 4.0, false, false))
        assertTrue(SourcePriorityPolicy.isPc60HeartRateAuthoritative(last + fresh, last, 80, 4.0, false, false))
        assertFalse(SourcePriorityPolicy.isPc60HeartRateAuthoritative(last + fresh + 1L, last, 80, 4.0, false, false))
    }
}
