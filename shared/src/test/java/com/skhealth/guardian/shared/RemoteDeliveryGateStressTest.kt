package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RemoteDeliveryGateStressTest {
    @Test
    fun acknowledgedAlarmNeverRetries() {
        repeat(1_000_000) { i ->
            val alertTs = i.toLong() + 1L
            assertFalse(RemoteDeliveryGate.shouldDeliver(alertTs, alertTs))
            assertFalse(RemoteDeliveryGate.shouldDeliver(alertTs + 1L, alertTs))
        }
    }

    @Test
    fun olderAcknowledgementDoesNotSuppressNewerAlarm() {
        repeat(1_000_000) { i ->
            val alertTs = i.toLong() + 2L
            assertTrue(RemoteDeliveryGate.shouldDeliver(alertTs - 1L, alertTs))
        }
    }

    @Test
    fun randomizedOrderingMatchesWatermarkRule() {
        val random = Random(20260909)
        repeat(1_000_000) {
            val ack = random.nextLong(0L, 10_000_000L)
            val alertTs = random.nextLong(0L, 10_000_000L)
            val expected = alertTs <= 0L || ack < alertTs
            if (expected) assertTrue(RemoteDeliveryGate.shouldDeliver(ack, alertTs))
            else assertFalse(RemoteDeliveryGate.shouldDeliver(ack, alertTs))
        }
    }
}
