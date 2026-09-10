package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlertResourcePolicyTest {
    private val alertA = "SPO2_CRITICAL:event:11111111-1111-1111-1111-111111111111"
    private val alertB = "HEART_RATE_HIGH_CONFIRMED:event:22222222-2222-2222-2222-222222222222"

    @Test
    fun concurrentExactAlarmsOwnDifferentNotifications() {
        val tagA = ExactAlertResourcePolicy.notificationTag(alertA)!!
        val tagB = ExactAlertResourcePolicy.notificationTag(alertB)!!

        assertFalse(tagA == tagB)
        assertTrue(ExactAlertResourcePolicy.ownsNotification(tagA, alertA))
        assertFalse(ExactAlertResourcePolicy.ownsNotification(tagB, alertA))
    }

    @Test
    fun acknowledgingAlarmARemovesOnlyItsResources() {
        val activeNotifications = setOf(alertA, alertB)
        val activeEscalations = setOf(alertA, alertB)

        val notificationsAfterAck = ExactAlertResourcePolicy.remainingAfterAcknowledgement(activeNotifications, alertA)
        val escalationsAfterAck = ExactAlertResourcePolicy.remainingAfterAcknowledgement(activeEscalations, alertA)

        assertEquals(setOf(alertB), notificationsAfterAck)
        assertEquals(setOf(alertB), escalationsAfterAck)
    }

    @Test
    fun delayedAckForOldAlarmCannotTouchNewAlarm() {
        val newAlarmOnly = setOf(alertB)
        assertEquals(
            newAlarmOnly,
            ExactAlertResourcePolicy.remainingAfterAcknowledgement(newAlarmOnly, alertA)
        )
    }

    @Test
    fun invalidOrTimestampIdentityCannotOwnExactNotification() {
        assertNull(ExactAlertResourcePolicy.notificationTag("bad-id"))
        assertNull(ExactAlertResourcePolicy.notificationTag("SPO2_CRITICAL:ts:1700000000000"))
        assertNull(ExactAlertResourcePolicy.notificationTag("SPO2_CRITICAL:event:"))
        assertFalse(AlertIdentity.isExact("WATCH_DISCONNECTED:ts:1700000000000"))
        assertTrue(AlertIdentity.isExact("WATCH_DISCONNECTED:event:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
    }
}
