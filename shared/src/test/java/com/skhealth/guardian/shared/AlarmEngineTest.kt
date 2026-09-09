package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEngineTest {
    private val config = AlarmConfig(
        spo2CriticalImmediate = 80,
        spo2LowThreshold = 90,
        spo2ConfirmCount = 2,
        heartRateHighThreshold = 130,
        heartRateHighConfirmCount = 2
    )

    @Test
    fun spo2Below80AlarmsImmediately() {
        val engine = AlarmEngine(config)
        val alerts = engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 79))
        assertEquals(1, alerts.size)
        assertEquals(AlertType.SPO2_CRITICAL, alerts.single().type)
    }

    @Test
    fun spo2From80To89RequiresTwoValidReadings() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 85)).isEmpty())
        val alerts = engine.evaluate(HealthReading(timestampMs = 2L, spo2 = 86))
        assertEquals(1, alerts.size)
        assertEquals(AlertType.SPO2_LOW_CONFIRMED, alerts.single().type)
    }

    @Test
    fun normalSpo2ResetsLowCounter() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 85)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 2L, spo2 = 95)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 3L, spo2 = 85)).isEmpty())
    }

    @Test
    fun invalidReadingDoesNotResetLowSpo2Counter() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 85)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 2L, valid = false)).isEmpty())
        val alerts = engine.evaluate(HealthReading(timestampMs = 3L, spo2 = 85))
        assertEquals(AlertType.SPO2_LOW_CONFIRMED, alerts.single().type)
    }

    @Test
    fun heartRateAbove130RequiresTwoValidReadings() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, heartRate = 131)).isEmpty())
        val alerts = engine.evaluate(HealthReading(timestampMs = 2L, heartRate = 140))
        assertEquals(1, alerts.size)
        assertEquals(AlertType.HEART_RATE_HIGH_CONFIRMED, alerts.single().type)
    }

    @Test
    fun normalHeartRateResetsHighCounter() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, heartRate = 140)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 2L, heartRate = 90)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 3L, heartRate = 140)).isEmpty())
    }
}
