package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EscalationGateStressTest {
    @Test
    fun acknowledgedSameOrNewerAlarmNeverEscalates() {
        repeat(1_000_000) { i ->
            val alertTs = i.toLong() + 1L
            assertFalse(EscalationGate.shouldEscalate(alertTs, alertTs))
            assertFalse(EscalationGate.shouldEscalate(alertTs + 1L, alertTs))
        }
    }

    @Test
    fun olderAckNeverSuppressesNewerAlarm() {
        repeat(1_000_000) { i ->
            val ackTs = i.toLong()
            val alertTs = ackTs + 1L
            assertTrue(EscalationGate.shouldEscalate(ackTs, alertTs))
        }
    }

    @Test
    fun randomizedOrderingMatchesTimestampRule() {
        val random = Random(0xA11CE)
        repeat(1_000_000) {
            val ackTs = random.nextLong(0L, 10_000_000L)
            val alertTs = random.nextLong(1L, 10_000_000L)
            val expected = ackTs < alertTs
            if (expected) assertTrue(EscalationGate.shouldEscalate(ackTs, alertTs))
            else assertFalse(EscalationGate.shouldEscalate(ackTs, alertTs))
        }
    }
}
