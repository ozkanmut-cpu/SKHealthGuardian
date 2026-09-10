package com.skhealth.guardian.shared

/**
 * Small deterministic retry boundary for safety-critical synchronous persistence.
 * The caller supplies the actual durable write (for example SharedPreferences.Editor.commit()).
 */
object DurableCommitRetryPolicy {
    const val DEFAULT_ATTEMPTS = 3

    fun commit(
        maxAttempts: Int = DEFAULT_ATTEMPTS,
        write: () -> Boolean
    ): Boolean {
        if (maxAttempts <= 0) return false
        repeat(maxAttempts) {
            if (write()) return true
        }
        return false
    }
}
