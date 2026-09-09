package com.skhealth.guardian.shared

object SourcePriorityPolicy {
    const val DEFAULT_PC60_FRESH_MS = 10_000L

    private fun isFresh(nowMs: Long, lastPacketAt: Long, freshMs: Long): Boolean {
        val age = nowMs - lastPacketAt
        return lastPacketAt > 0L && age in 0..freshMs
    }

    fun isPc60Spo2Authoritative(
        nowMs: Long,
        lastPacketAt: Long,
        spo2: Int?,
        perfusionIndex: Double?,
        probeOff: Boolean,
        pulseSearching: Boolean,
        freshMs: Long = DEFAULT_PC60_FRESH_MS
    ): Boolean = isFresh(nowMs, lastPacketAt, freshMs) &&
        !probeOff && !pulseSearching && spo2 in 1..100 && (perfusionIndex ?: 0.0) > 0.0

    fun isPc60HeartRateAuthoritative(
        nowMs: Long,
        lastPacketAt: Long,
        heartRate: Int?,
        perfusionIndex: Double?,
        probeOff: Boolean,
        pulseSearching: Boolean,
        freshMs: Long = DEFAULT_PC60_FRESH_MS
    ): Boolean = isFresh(nowMs, lastPacketAt, freshMs) &&
        !probeOff && !pulseSearching && heartRate in 1..511 && (perfusionIndex ?: 0.0) > 0.0

    /** Backward-compatible whole-device authority: true only when both metrics are usable. */
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
        return fresh && !probeOff && !pulseSearching && spo2 in 1..100
    }
}
