package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MonotonicTimestampGateStressTest {
    @Test
    fun staleSamplesAreRejectedAcrossReconnectBoundary() {
        val gate = MonotonicTimestampGate(10_000L)
        assertFalse(gate.accept(9_999L))
        assertTrue(gate.accept(10_000L))
        assertTrue(gate.accept(10_001L))
        assertFalse(gate.accept(10_000L))
        assertEquals(10_001L, gate.lastAccepted())
    }

    @Test
    fun millionMixedTimestampsNeverMoveWatermarkBackwards() {
        val gate = MonotonicTimestampGate()
        var expected = 0L
        repeat(1_000_000) { i ->
            val ts = if (i % 5 == 0) (i - 3).coerceAtLeast(0).toLong() else i.toLong()
            val accepted = gate.accept(ts)
            if (ts >= expected) {
                assertTrue(accepted)
                expected = ts
            } else {
                assertFalse(accepted)
            }
            assertEquals(expected, gate.lastAccepted())
        }
    }

    @Test
    fun parallelReconnectBurstsConvergeToMaximumTimestamp() {
        val gate = MonotonicTimestampGate()
        val executor = Executors.newFixedThreadPool(8)
        try {
            val jobs = (0 until 8).map { worker ->
                Callable {
                    repeat(100_000) { i ->
                        val base = worker * 100_000L + i
                        gate.accept(base)
                        if (i % 7 == 0) gate.accept((base - 50).coerceAtLeast(0L))
                    }
                }
            }
            executor.invokeAll(jobs).forEach { it.get() }
            assertEquals(799_999L, gate.lastAccepted())
        } finally {
            executor.shutdownNow()
        }
    }
}
