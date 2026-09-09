package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEpisodeStressTest {
    private val config = AlarmConfig(
        spo2CriticalImmediate = 80,
        spo2LowThreshold = 90,
        spo2ConfirmCount = 2,
        heartRateHighThreshold = 130,
        heartRateHighConfirmCount = 2
    )

    @Test
    fun hundredThousandCriticalEpisodesEmitExactlyOncePerRecoveryCycle() {
        val engine = AlarmEngine(config)
        var alerts = 0
        var ts = 1L
        repeat(100_000) {
            alerts += engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 79)).size
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 78)).isEmpty())
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 95)).isEmpty())
        }
        assertEquals(100_000, alerts)
    }

    @Test
    fun fiftyThousandLowToCriticalEscalationsEmitTwoSeverityEventsOnly() {
        val engine = AlarmEngine(config)
        var lowAlerts = 0
        var criticalAlerts = 0
        var ts = 1L
        repeat(50_000) {
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 86)).isEmpty())
            engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 85)).forEach {
                if (it.type == AlertType.SPO2_LOW_CONFIRMED) lowAlerts++
            }
            engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 79)).forEach {
                if (it.type == AlertType.SPO2_CRITICAL) criticalAlerts++
            }
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 78)).isEmpty())
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 86)).isEmpty())
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, spo2 = 95)).isEmpty())
        }
        assertEquals(50_000, lowAlerts)
        assertEquals(50_000, criticalAlerts)
    }

    @Test
    fun fiftyThousandHighHeartRateEpisodesNeedRecoveryBeforeRealert() {
        val engine = AlarmEngine(config)
        var alerts = 0
        var ts = 1L
        repeat(50_000) {
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, heartRate = 140)).isEmpty())
            alerts += engine.evaluate(HealthReading(timestampMs = ts++, heartRate = 141)).size
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, heartRate = 150)).isEmpty())
            assertTrue(engine.evaluate(HealthReading(timestampMs = ts++, heartRate = 100)).isEmpty())
        }
        assertEquals(50_000, alerts)
    }
}
