package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class Pc60HeartRatePolicyTest {
    private fun sample(t: Long, hr: Int, spo2: Int = 96, pi: Double = 2.0, probeOff: Boolean = false) =
        Pc60Sample(t, spo2, hr, pi, probeOff, false)

    @Test fun highHrRequiresSecondQualifiedReadingAfterInterval() {
        val p = Pc60HeartRatePolicy(highThreshold = 130, highConfirmCount = 2, confirmIntervalMs = 120_000L)
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(0L, 140)))
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(60_000L, 145)))
        assertEquals(Pc60HrDecision.HIGH_ALARM, p.evaluate(sample(120_000L, 142)))
    }

    @Test fun normalReadingResetsHighHrConfirmation() {
        val p = Pc60HeartRatePolicy(highThreshold = 130, highConfirmCount = 2, confirmIntervalMs = 120_000L)
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(0L, 140)))
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(90_000L, 100)))
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(120_000L, 145)))
        assertEquals(Pc60HrDecision.HIGH_ALARM, p.evaluate(sample(240_000L, 145)))
    }

    @Test fun invalidSignalResetsConfirmation() {
        val p = Pc60HeartRatePolicy(highThreshold = 130, highConfirmCount = 2, confirmIntervalMs = 120_000L)
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(0L, 140)))
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(60_000L, 145, probeOff = true)))
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(120_000L, 145)))
    }

    @Test fun lowHrUsesSameTimedConfirmationWhenEnabled() {
        val p = Pc60HeartRatePolicy(lowEnabled = true, lowThreshold = 45, lowConfirmCount = 2, confirmIntervalMs = 120_000L)
        assertEquals(Pc60HrDecision.NONE, p.evaluate(sample(0L, 40)))
        assertEquals(Pc60HrDecision.LOW_ALARM, p.evaluate(sample(120_000L, 42)))
    }
}
