package com.skhealth.guardian.mobile

import android.content.Context

object SourcePriorityCoordinator {
    private const val PC60_FRESH_MS = 10_000L

    fun isPc60Authoritative(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = Pc60StatusStore.load(context)
        val fresh = s.lastPacketAt > 0L && nowMs - s.lastPacketAt in 0..PC60_FRESH_MS
        val physiologic = !s.probeOff && !s.pulseSearching && (s.spo2 ?: 0) in 1..100
        return fresh && physiologic
    }

    fun activeSourceLabel(context: Context, nowMs: Long = System.currentTimeMillis()): String =
        if (isPc60Authoritative(context, nowMs)) "PC-60FW" else "Galaxy Watch"
}
