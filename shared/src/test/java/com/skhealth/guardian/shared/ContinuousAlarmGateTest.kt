package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAlarmGateTest {
    private val config = AlarmConfig(
        spo2CriticalImmediate = 80,
        spo2LowThreshold = 90,
        spo2ConfirmCount = 2,
        heartRateHighThreshold = 130,
        heartRateHighConfirmCount = 2
    )

    @Test
    fun criticalSpo2DoesNotAlarmImmediatelyOnPc60() {
        val gate = ContinuousAlarmGate(config)
        assertTrue(gate.select(HealthReading(timestampMs = 0L, spo2 = 79, source = "pc60fw")).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 119_999L, spo2 = 79, source = "pc60fw")).isEmpty())
        val out = gate.select(HealthReading(timestampMs = 120_000L, spo2 = 79, source = "pc60fw"))
        assertEquals(1, out.size)
        assertEquals(79, out.single().spo2)
    }

    @Test
    fun lowSpo2At85AlarmsOnlyAfterTwoMinutes() {
        val gate = ContinuousAlarmGate(config)
        assertTrue(gate.select(HealthReading(timestampMs = 0L, spo2 = 85)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 1_000L, spo2 = 85)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 119_999L, spo2 = 85)).isEmpty())
        assertEquals(2, gate.select(HealthReading(timestampMs = 120_000L, spo2 = 85)).size)
    }

    @Test
    fun stableRecoveryAbove85CancelsPendingAlarm() {
        val gate = ContinuousAlarmGate(config)
        assertTrue(gate.select(HealthReading(timestampMs = 0L, spo2 = 79)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 60_000L, spo2 = 86)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 69_999L, spo2 = 86)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 70_000L, spo2 = 86)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 130_000L, spo2 = 79)).isEmpty())
    }

    @Test
    fun briefRecoveryAbove85DoesNotCancelPendingAlarm() {
        val gate = ContinuousAlarmGate(config)
        assertTrue(gate.select(HealthReading(timestampMs = 0L, spo2 = 79)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 60_000L, spo2 = 86)).isEmpty())
        assertTrue(gate.select(HealthReading(timestampMs = 65_000L, spo2 = 84)).isEmpty())
        val out = gate.select(HealthReading(timestampMs = 120_000L, spo2 = 84))
        assertEquals(2, out.size)
    }

    @Test
    fun highHeartRateSecondEvaluationWaitsTwoMinutes() {
        val gate = ContinuousAlarmGate(config)
        assertEquals(1, gate.select(HealthReading(timestampMs = 0L, heartRate = 140)).size)
        assertTrue(gate.select(HealthReading(timestampMs = 1_000L, heartRate = 140)).isEmpty())
        assertEquals(1, gate.select(HealthReading(timestampMs = 120_000L, heartRate = 140)).size)
    }
}
