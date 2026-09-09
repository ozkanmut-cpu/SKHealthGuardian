package com.skhealth.guardian.shared

object SmsRetryPolicy {
    const val MAX_RETRIES = 2

    fun nextDelayMs(attempt: Int): Long? = when (attempt) {
        0 -> 30_000L
        1 -> 90_000L
        else -> null
    }
}
