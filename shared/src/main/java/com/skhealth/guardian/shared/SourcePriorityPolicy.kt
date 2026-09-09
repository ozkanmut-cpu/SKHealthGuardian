package com.skhealth.guardian.shared

object SourcePriorityPolicy {
    const val DEFAULT_PC60_FRESH_MS = 10_000L

    fun isPc60Authoritative(
        nowMs: Long,
        lastPacketAt: Long,
        spo2: Int?,
        probeOff: Boolean,
        pulseSearching: Boolean,
        freshMs: Long = DEFAULT_PC60_FRESH_MS
    ): Boolean {
        val age = nowMs - lastPacketAt
        val fresh = lastPacketAt > 0L && age in 0..freshMs
        val physiologic = !probeOff && !pulseSearching && spo2 in 1..100
        return fresh && physiologic
    }
}
