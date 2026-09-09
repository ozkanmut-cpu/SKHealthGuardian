package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsRetryPolicyTest {
    @Test fun firstFailureRetriesAfter30Seconds() {
        assertEquals(30_000L, SmsRetryPolicy.nextDelayMs(0))
    }

    @Test fun secondFailureRetriesAfter90Seconds() {
        assertEquals(90_000L, SmsRetryPolicy.nextDelayMs(1))
    }

    @Test fun noRetryAfterTwoRetries() {
        assertNull(SmsRetryPolicy.nextDelayMs(2))
        assertNull(SmsRetryPolicy.nextDelayMs(3))
    }
}
