package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEngineTest {
    @Test fun spo2Below80AlarmsImmediately() {
        val e = AlarmEngine()
        val alerts = e.evaluate(HealthReading(timestampMs=1, spo2=79))
        assertEquals(AlertType.SPO2_CRITICAL, alerts.single().type)
    }

    @Test fun spo2LowNeedsTwoReadings() {
        val e = AlarmEngine()
        assertTrue(e.evaluate(HealthReading(timestampMs=1, spo2=88)).isEmpty())
        assertEquals(AlertType.SPO2_LOW_CONFIRMED, e.evaluate(HealthReading(timestampMs=2, spo2=87)).single().type)
    }

    @Test fun highHeartRateNeedsTwoReadings() {
        val e = AlarmEngine()
        assertTrue(e.evaluate(HealthReading(timestampMs=1, heartRate=131)).isEmpty())
        assertEquals(AlertType.HEART_RATE_HIGH_CONFIRMED, e.evaluate(HealthReading(timestampMs=2, heartRate=135)).single().type)
    }
}
