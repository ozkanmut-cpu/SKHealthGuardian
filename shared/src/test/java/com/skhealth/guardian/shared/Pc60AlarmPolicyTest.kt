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
    fun below75AlarmsImmediately() {
        val p = Pc60AlarmPolicy()
        assertEquals(Pc60Decision.ALARM_IMMEDIATE, p.evaluate(sample(0L, 74)))
    }

    @Test
    fun exactly75DoesNotImmediateAlarm() {
        val p = Pc60AlarmPolicy()
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(0L, 75)))
    }

    @Test
    fun below85AtThreeMinutesAlarmsEarly() {
        val p = Pc60AlarmPolicy()
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(0L, 80)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(179_999L, 84)))
        assertEquals(Pc60Decision.ALARM_EARLY, p.evaluate(sample(180_000L, 84)))
    }

    @Test
    fun reaching85AvoidsEarlyAlarmButStillRequires90() {
        val p = Pc60AlarmPolicy()
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(0L, 80)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(120_000L, 86)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(180_000L, 86)))
        assertEquals(Pc60Decision.ALARM_TIMEOUT, p.evaluate(sample(300_000L, 89)))
    }

    @Test
    fun stable90RecoveryCancelsObservation() {
        val p = Pc60AlarmPolicy()
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(0L, 80)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(120_000L, 86)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(200_000L, 90)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(214_999L, 91)))
        assertEquals(Pc60Decision.RECOVERED, p.evaluate(sample(215_000L, 90)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(400_000L, 92)))
    }

    @Test
    fun short90SpikeDoesNotRecover() {
        val p = Pc60AlarmPolicy()
        p.evaluate(sample(0L, 80))
        p.evaluate(sample(100_000L, 90))
        p.evaluate(sample(105_000L, 89))
        assertEquals(Pc60Decision.ALARM_TIMEOUT, p.evaluate(sample(300_000L, 89)))
    }

    @Test
    fun invalidSignalResetsObservationWindow() {
        val p = Pc60AlarmPolicy()
        p.evaluate(sample(0L, 80))
        p.evaluate(sample(120_000L, 0, valid = false))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(180_000L, 80)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(359_999L, 84)))
        assertEquals(Pc60Decision.ALARM_EARLY, p.evaluate(sample(360_000L, 84)))
    }

    @Test
    fun alarmIsLatchedUntilStableRecovery() {
        val p = Pc60AlarmPolicy()
        assertEquals(Pc60Decision.ALARM_IMMEDIATE, p.evaluate(sample(0L, 70)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(1_000L, 69)))
        assertEquals(Pc60Decision.NONE, p.evaluate(sample(20_000L, 90)))
        assertEquals(Pc60Decision.RECOVERED, p.evaluate(sample(35_000L, 91)))
    }
}
