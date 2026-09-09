package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class OverdueEscalationPolicyTest {
    @Test
    fun restoresAtExactMaxAgeBoundary() {
        val now = 1_000_000L
        val maxAge = 60_000L
        assertEquals(
            OverdueEscalationPolicy.Decision.RESTORE,
            OverdueEscalationPolicy.decide(now, now - maxAge, maxAge)
        )
    }

    @Test
    fun expiresOneMillisecondPastBoundary() {
        val now = 1_000_000L
        val maxAge = 60_000L
        assertEquals(
            OverdueEscalationPolicy.Decision.EXPIRE,
            OverdueEscalationPolicy.decide(now, now - maxAge - 1L, maxAge)
        )
    }

    @Test
    fun futureClockTimestampDoesNotExpire() {
        val now = 1_000_000L
        assertEquals(
            OverdueEscalationPolicy.Decision.RESTORE,
            OverdueEscalationPolicy.decide(now, now + 30_000L, 60_000L)
        )
    }

    @Test
    fun invalidInputsExpireSafely() {
        assertEquals(OverdueEscalationPolicy.Decision.EXPIRE, OverdueEscalationPolicy.decide(0L, 1L, 1L))
        assertEquals(OverdueEscalationPolicy.Decision.EXPIRE, OverdueEscalationPolicy.decide(1L, 0L, 1L))
        assertEquals(OverdueEscalationPolicy.Decision.EXPIRE, OverdueEscalationPolicy.decide(1L, 1L, 0L))
    }
}
