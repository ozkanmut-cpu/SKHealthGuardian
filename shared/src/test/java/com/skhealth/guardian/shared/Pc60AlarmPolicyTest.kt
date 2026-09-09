package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class Pc60AlarmPolicyTest {
    private fun sample(ts: Long, spo2: Int, valid: Boolean = true): Pc60Sample =
        Pc60Sample(
            timestampMs = ts,
            spo2 = if (valid) spo2 else 0,
            pulseRate = if (valid) 78 else 0,
            perfusionIndex = if (valid) 4.0 else 0.0,
            probeOff = !valid,
            pulseSearching = false
        )

    @Test
    fun lowValueMustPersistForTwoMinutes() {
        val p = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(0L, 80)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(119_999L, 82)))
        assertEquals(Pc60Decision.ALARM, p.evaluate(sample(120_000L, 84)))
    }

    @Test
    fun stableRecoveryAbove85CancelsPendingAlarm() {
        val p = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(0L, 79)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(30_000L, 86)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(39_999L, 87)))
        assertEquals(Pc60Decision.RECOVERED, p.evaluate(sample(40_000L, 88)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(130_000L, 84)))
    }

    @Test
    fun oneShortRecoverySpikeDoesNotCancel() {
        val p = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)
        p.evaluate(sample(0L, 80))
        p.evaluate(sample(30_000L, 86))
        p.evaluate(sample(35_000L, 84))
        assertEquals(Pc60Decision.ALARM, p.evaluate(sample(120_000L, 84)))
    }

    @Test
    fun invalidSignalResetsObservationWindow() {
        val p = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)
        p.evaluate(sample(0L, 80))
        p.evaluate(sample(60_000L, 0, valid = false))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(120_000L, 80)))
        assertEquals(Pc60Decision.ALARM, p.evaluate(sample(240_000L, 80)))
    }

    @Test
    fun runtimeSettingChangeResetsPendingState() {
        val p = Pc60AlarmPolicy(85, 120_000L, 85, 10_000L)
        p.evaluate(sample(0L, 80))
        p.update(83, 180_000L, 86, 15_000L)
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(120_000L, 82)))
    }
}
