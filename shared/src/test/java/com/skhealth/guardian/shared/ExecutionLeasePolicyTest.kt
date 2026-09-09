package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionLeasePolicyTest {
    @Test
    fun firstExecutionCanAcquire() {
        assertTrue(ExecutionLeasePolicy.canAcquire(false, 0L, 1_000L, 120_000L))
    }

    @Test
    fun activeLeaseBlocksDuplicate() {
        assertFalse(ExecutionLeasePolicy.canAcquire(false, 1_000L, 120_999L, 120_000L))
    }

    @Test
    fun leaseCanBeRecoveredAtExactExpiry() {
        assertTrue(ExecutionLeasePolicy.canAcquire(false, 1_000L, 121_000L, 120_000L))
    }

    @Test
    fun deliveredEscalationNeverReacquires() {
        assertFalse(ExecutionLeasePolicy.canAcquire(true, 0L, 1_000L, 120_000L))
        assertFalse(ExecutionLeasePolicy.canAcquire(true, 1_000L, 999_999L, 120_000L))
    }

    @Test
    fun clockRollbackKeepsExistingLeaseActive() {
        assertFalse(ExecutionLeasePolicy.canAcquire(false, 200_000L, 100_000L, 120_000L))
    }

    @Test
    fun invalidClockOrLeaseCannotAcquire() {
        assertFalse(ExecutionLeasePolicy.canAcquire(false, 0L, 0L, 120_000L))
        assertFalse(ExecutionLeasePolicy.canAcquire(false, 0L, 1_000L, 0L))
    }
}
