package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertIdentityAndAckPolicyTest {
    @Test
    fun measurementBackedAlertIdentityIgnoresClockRollback() {
        val readingId = "reading-uuid-123"
        val a = AlertIdentity.of(AlertType.SPO2_CRITICAL, readingId, 9_000_000L)
        val b = AlertIdentity.of(AlertType.SPO2_CRITICAL, readingId, 1_000L)
        assertEquals(a, b)
    }

    @Test
    fun differentReadingsNeverShareAlarmIdentityEvenWithSameTimestamp() {
        val a = AlertIdentity.of(AlertType.SPO2_CRITICAL, "reading-a", 1234L)
        val b = AlertIdentity.of(AlertType.SPO2_CRITICAL, "reading-b", 1234L)
        assertFalse(a == b)
    }

    @Test
    fun differentAlarmTypesNeverShareIdentityForSameReading() {
        val a = AlertIdentity.of(AlertType.SPO2_CRITICAL, "reading-a", 1234L)
        val b = AlertIdentity.of(AlertType.HEART_RATE_HIGH_CONFIRMED, "reading-a", 1234L)
        assertFalse(a == b)
    }

    @Test
    fun currentTechnicalAlertsUseEventUuidNotTimestamp() {
        val a = AlertEvent(AlertType.DATA_STALE, 100L, null, "stale", eventId = "event-a")
        val b = AlertEvent(AlertType.DATA_STALE, 100L, null, "stale", eventId = "event-b")
        val rolledBackSameEvent = a.copy(timestampMs = 1L)

        assertFalse(AlertIdentity.of(a) == AlertIdentity.of(b))
        assertEquals(AlertIdentity.of(a), AlertIdentity.of(rolledBackSameEvent))
        assertTrue(AlertIdentity.of(a).contains(":event:event-a"))
    }

    @Test
    fun legacyTechnicalIdentityOverloadKeepsTimestampFallback() {
        val a = AlertIdentity.of(AlertType.DATA_STALE, null, 100L)
        val b = AlertIdentity.of(AlertType.DATA_STALE, null, 101L)
        assertFalse(a == b)
        assertTrue(a.contains(":ts:100"))
    }

    @Test
    fun v2PendingAckOrderingUsesSequenceNotSensorClock() {
        val olderUserAction = "v2|10|SPO2_CRITICAL:old-reading|9999999|old"
        val newerUserActionWithRolledBackClock = "v2|11|SPO2_CRITICAL:new-reading|100|new"
        assertEquals(newerUserActionWithRolledBackClock, PendingAckPolicy.newest(olderUserAction, newerUserActionWithRolledBackClock))
    }

    @Test
    fun delayedOlderV2CallbackCannotReplaceNewerPendingAck() {
        val newer = "v2|500|SPO2_CRITICAL:new|100|new"
        val delayedOld = "v2|499|SPO2_CRITICAL:old|99999999|old"
        assertEquals(newer, PendingAckPolicy.newest(newer, delayedOld))
    }

    @Test
    fun legacyPendingAckOrderingRemainsTimestampCompatible() {
        assertEquals("200|new", PendingAckPolicy.newest("100|old", "200|new"))
    }
}
