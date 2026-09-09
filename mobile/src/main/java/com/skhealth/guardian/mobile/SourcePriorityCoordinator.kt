package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.SourcePriorityPolicy

object SourcePriorityCoordinator {
    fun isPc60Authoritative(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = Pc60StatusStore.load(context)
        return SourcePriorityPolicy.isPc60Authoritative(
            nowMs = nowMs,
            lastPacketAt = s.lastPacketAt,
            spo2 = s.spo2,
            probeOff = s.probeOff,
            pulseSearching = s.pulseSearching
        )
    }

    fun activeSourceLabel(context: Context, nowMs: Long = System.currentTimeMillis()): String =
        if (isPc60Authoritative(context, nowMs)) "PC-60FW" else "Galaxy Watch"
}
