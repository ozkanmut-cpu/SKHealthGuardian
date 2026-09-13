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
    fun exactly75StartsFollowUpWithoutImmediateAlarm() {
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
    fun reaching85ClosesFollowUpImmediately() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 80, true))
        assertEquals(WatchSpO2Decision.RECOVERED, p.evaluate(60_000L, 85, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(300_000L, 88, true))
    }

    @Test
    fun readingAbove85NeverStartsFollowUp() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 88, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(300_000L, 89, true))
    }

    @Test
    fun invalidSampleDoesNotCreateHealthAlarmOrEraseLowWindow() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(0L, 82, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(120_000L, null, false))
        assertEquals(WatchSpO2Decision.ALARM_EARLY, p.evaluate(180_000L, 83, true))
    }

    @Test
    fun recoveryAfterAlarmAllowsANewEpisode() {
        val p = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.ALARM_IMMEDIATE, p.evaluate(0L, 74, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(30_000L, 82, true))
        assertEquals(WatchSpO2Decision.RECOVERED, p.evaluate(60_000L, 86, true))
        assertEquals(WatchSpO2Decision.NONE, p.evaluate(120_000L, 80, true))
        assertEquals(WatchSpO2Decision.ALARM_EARLY, p.evaluate(300_000L, 84, true))
    }

    @Test
    fun restoredLowEpisodeKeepsOriginalThreeMinuteWindow() {
        val first = WatchSpO2AlarmPolicy()
        assertEquals(WatchSpO2Decision.NONE, first.evaluate(10_000L, 82, true))

        val restored = WatchSpO2AlarmPolicy().also { it.restore(first.snapshot()) }
        assertEquals(WatchSpO2Decision.NONE, restored.evaluate(160_000L, 83, true))
        assertEquals(WatchSpO2Decision.ALARM_EARLY, restored.evaluate(190_000L, 84, true))
    }
}
