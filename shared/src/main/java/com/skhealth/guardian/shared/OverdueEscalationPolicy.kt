package com.skhealth.guardian.shared

object OverdueEscalationPolicy {
    enum class Decision { RESTORE, EXPIRE }

    fun decide(nowMs: Long, alertTimestampMs: Long, maxAgeMs: Long): Decision {
        if (nowMs <= 0L || alertTimestampMs <= 0L || maxAgeMs <= 0L) return Decision.EXPIRE
        val ageMs = (nowMs - alertTimestampMs).coerceAtLeast(0L)
        return if (ageMs <= maxAgeMs) Decision.RESTORE else Decision.EXPIRE
    }
}
