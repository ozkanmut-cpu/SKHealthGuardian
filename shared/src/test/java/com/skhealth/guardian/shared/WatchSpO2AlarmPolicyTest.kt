package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSpO2AlarmPolicyTest {
    @Test
    fun below75AlarmsImmediately() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.ALARM_IMMEDIATE, p.evaluate(0L, 74, true))
    }

    @Test
    fun exactly75DoesNotAlarmImmediately() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 75, true))
    }

    @Test
    fun below85AtThreeMinutesAlarms() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 82, true))
        assertEquals(WatchSpO2Decision.ALARM_EARLY, p.evaluate(180_000L, 84, true))
    }

    @Test
    fun reaching85AvoidsEarlyAlarmButStillNeeds90() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 80, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(180_000L, 86, true))
        assertEquals(WatchSpO2Decision.ALARM_TIMEOUT, p.evaluate(300_000L, 88, true))
    }

    @Test
    fun stable90Recovers() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 84, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(120_000L, 90, true))
        assertEquals(WatchSpO2Decision.RECOVERED, p.evaluate(135_000L, 91, true))
    }

    @Test
    fun invalidSampleDoesNotCreateHealthAlarmOrEraseLowWindow() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 82, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(120_000L, null, false))
        assertEquals(WatchSpO2Decision.ALARM_EARLY, p.evaluate(180_000L, 83, true))
    }

    @Test
    fun invalidSampleBreaksRecoveryStreak() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 84, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(120_000L, 90, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(130_000L, null, false))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(140_000L, 91, true))
        assertEquals(WatchSpO2Decision.RECOVERED, p.evaluate(155_000L, 92, true))
    }
}
