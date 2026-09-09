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
    fun continuousCriticalSpo2ProducesOneAlarmUntilRecovery() {
        val engine = AlarmEngine(config)
        assertEquals(AlertType.SPO2_CRITICAL, engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 79)).single().type)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 2L, spo2 = 78)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 3L, spo2 = 76)).isEmpty())
    }

    @Test
    fun lowEpisodeCanEscalateOnceToCritical() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 86)).isEmpty())
        assertEquals(AlertType.SPO2_LOW_CONFIRMED, engine.evaluate(HealthReading(timestampMs = 2L, spo2 = 85)).single().type)
        assertEquals(AlertType.SPO2_CRITICAL, engine.evaluate(HealthReading(timestampMs = 3L, spo2 = 79)).single().type)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 4L, spo2 = 78)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 5L, spo2 = 85)).isEmpty())
    }

    @Test
    fun recoveryAllowsNewSpo2Episode() {
        val engine = AlarmEngine(config)
        assertEquals(AlertType.SPO2_CRITICAL, engine.evaluate(HealthReading(timestampMs = 1L, spo2 = 79)).single().type)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 2L, spo2 = 95)).isEmpty())
        assertEquals(AlertType.SPO2_CRITICAL, engine.evaluate(HealthReading(timestampMs = 3L, spo2 = 78)).single().type)
    }

    @Test
    fun restoredCriticalEpisodeDoesNotAlarmAgainUntilRecovery() {
        val first = AlarmEngine(config)
        assertEquals(AlertType.SPO2_CRITICAL, first.evaluate(HealthReading(timestampMs = 1L, spo2 = 79)).single().type)
        val restored = AlarmEngine(config)
        restored.restore(first.snapshot())
        assertTrue(restored.evaluate(HealthReading(timestampMs = 2L, spo2 = 78)).isEmpty())
        assertTrue(restored.evaluate(HealthReading(timestampMs = 3L, spo2 = 95)).isEmpty())
        assertEquals(AlertType.SPO2_CRITICAL, restored.evaluate(HealthReading(timestampMs = 4L, spo2 = 77)).single().type)
    }

    @Test
    fun restoredPendingLowCounterContinuesConfirmation() {
        val first = AlarmEngine(config)
        assertTrue(first.evaluate(HealthReading(timestampMs = 1L, spo2 = 85)).isEmpty())
        val restored = AlarmEngine(config)
        restored.restore(first.snapshot())
        assertEquals(AlertType.SPO2_LOW_CONFIRMED, restored.evaluate(HealthReading(timestampMs = 2L, spo2 = 86)).single().type)
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
    fun continuousHighHeartRateProducesOneAlarmUntilRecovery() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, heartRate = 140)).isEmpty())
        assertEquals(AlertType.HEART_RATE_HIGH_CONFIRMED, engine.evaluate(HealthReading(timestampMs = 2L, heartRate = 141)).single().type)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 3L, heartRate = 150)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 4L, heartRate = 155)).isEmpty())
    }

    @Test
    fun recoveryAllowsNewHighHeartRateEpisode() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, heartRate = 140)).isEmpty())
        assertEquals(AlertType.HEART_RATE_HIGH_CONFIRMED, engine.evaluate(HealthReading(timestampMs = 2L, heartRate = 141)).single().type)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 3L, heartRate = 100)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 4L, heartRate = 142)).isEmpty())
        assertEquals(AlertType.HEART_RATE_HIGH_CONFIRMED, engine.evaluate(HealthReading(timestampMs = 5L, heartRate = 143)).single().type)
    }

    @Test
    fun normalHeartRateResetsHighCounter() {
        val engine = AlarmEngine(config)
        assertTrue(engine.evaluate(HealthReading(timestampMs = 1L, heartRate = 140)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 2L, heartRate = 90)).isEmpty())
        assertTrue(engine.evaluate(HealthReading(timestampMs = 3L, heartRate = 140)).isEmpty())
    }
}
