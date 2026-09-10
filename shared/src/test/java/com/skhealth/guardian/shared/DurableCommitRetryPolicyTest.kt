package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableCommitRetryPolicyTest {
    @Test
    fun succeedsImmediatelyWithoutExtraWrites() {
        var attempts = 0
        val ok = DurableCommitRetryPolicy.commit {
            attempts++
            true
        }
        assertTrue(ok)
        assertEquals(1, attempts)
    }

    @Test
    fun transientFailuresRecoverWithinRetryBudget() {
        var attempts = 0
        val ok = DurableCommitRetryPolicy.commit {
            attempts++
            attempts == 3
        }
        assertTrue(ok)
        assertEquals(3, attempts)
    }

    @Test
    fun permanentFailureNeverReportsDurableSuccess() {
        var attempts = 0
        val ok = DurableCommitRetryPolicy.commit {
            attempts++
            false
        }
        assertFalse(ok)
        assertEquals(DurableCommitRetryPolicy.DEFAULT_ATTEMPTS, attempts)
    }

    @Test
    fun zeroRetryBudgetCannotExecuteWrite() {
        var attempts = 0
        val ok = DurableCommitRetryPolicy.commit(maxAttempts = 0) {
            attempts++
            true
        }
        assertFalse(ok)
        assertEquals(0, attempts)
    }

    @Test
    fun failureRecoveryStressNeverReturnsFalseSuccess() {
        var seed = 0x5A17C0DEL
        repeat(250_000) { index ->
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val succeedOn = ((seed ushr 1) % 5L).toInt() // 0 means all three attempts fail.
            var attempts = 0
            val ok = DurableCommitRetryPolicy.commit {
                attempts++
                succeedOn in 1..3 && attempts >= succeedOn
            }
            val expected = succeedOn in 1..3
            if (ok != expected) {
                throw AssertionError("iteration=$index succeedOn=$succeedOn attempts=$attempts ok=$ok")
            }
            if (attempts !in 1..3) {
                throw AssertionError("iteration=$index invalid attempts=$attempts")
            }
        }
    }
}
