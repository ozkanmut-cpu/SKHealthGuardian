package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PendingAckPolicyStressTest {
    @Test
    fun olderOfflineAckNeverOverwritesNewerOne() {
        var pending: String? = null
        pending = PendingAckPolicy.newest(pending, "100|old")
        pending = PendingAckPolicy.newest(pending, "300|new")
        pending = PendingAckPolicy.newest(pending, "200|late-old")
        assertEquals("300|new", pending)
    }

    @Test
    fun millionOfflineAckMergesConvergeToNewestTimestamp() {
        val random = Random(0x51A7)
        var pending: String? = null
        var maxTs = Long.MIN_VALUE
        repeat(1_000_000) { i ->
            val ts = random.nextLong(0L, 10_000_000L)
            maxTs = maxOf(maxTs, ts)
            pending = PendingAckPolicy.newest(pending, "$ts|ack-$i")
            assertEquals(maxTs, pending!!.substringBefore('|').toLong())
        }
    }
}
