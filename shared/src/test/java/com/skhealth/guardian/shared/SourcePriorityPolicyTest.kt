package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePriorityPolicyTest {
    private val now = 100_000L

    @Test fun freshValidPc60IsAuthoritative() {
        assertTrue(SourcePriorityPolicy.isPc60Authoritative(now, now - 1_000, 92, false, false))
    }

    @Test fun stalePc60FallsBackToWatch() {
        assertFalse(SourcePriorityPolicy.isPc60Authoritative(now, now - 10_001, 92, false, false))
    }

    @Test fun probeOffFallsBackToWatch() {
        assertFalse(SourcePriorityPolicy.isPc60Authoritative(now, now - 1_000, 92, true, false))
    }

    @Test fun pulseSearchingFallsBackToWatch() {
        assertFalse(SourcePriorityPolicy.isPc60Authoritative(now, now - 1_000, 92, false, true))
    }

    @Test fun invalidSpo2FallsBackToWatch() {
        assertFalse(SourcePriorityPolicy.isPc60Authoritative(now, now - 1_000, 0, false, false))
    }

    @Test fun futureTimestampDoesNotBecomeAuthoritative() {
        assertFalse(SourcePriorityPolicy.isPc60Authoritative(now, now + 1, 92, false, false))
    }

    @Test fun validSpo2ButInvalidPulseKeepsOnlySpo2OnPc60() {
        assertTrue(SourcePriorityPolicy.isPc60Spo2Authoritative(now, now - 1_000, 92, 3.2, false, false))
        assertFalse(SourcePriorityPolicy.isPc60HeartRateAuthoritative(now, now - 1_000, 0, 3.2, false, false))
    }

    @Test fun validPulseButInvalidSpo2KeepsOnlyHrOnPc60() {
        assertFalse(SourcePriorityPolicy.isPc60Spo2Authoritative(now, now - 1_000, 0, 2.1, false, false))
        assertTrue(SourcePriorityPolicy.isPc60HeartRateAuthoritative(now, now - 1_000, 84, 2.1, false, false))
    }

    @Test fun missingPerfusionIndexFallsBackPerMetric() {
        assertFalse(SourcePriorityPolicy.isPc60Spo2Authoritative(now, now - 1_000, 92, null, false, false))
        assertFalse(SourcePriorityPolicy.isPc60HeartRateAuthoritative(now, now - 1_000, 84, null, false, false))
    }
}
