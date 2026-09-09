package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.SourcePriorityPolicy

object SourcePriorityCoordinator {
    fun isPc60Spo2Authoritative(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = Pc60StatusStore.load(context)
        return SourcePriorityPolicy.isPc60Spo2Authoritative(
            nowMs = nowMs,
            lastPacketAt = s.lastPacketAt,
            spo2 = s.spo2,
            perfusionIndex = s.perfusionIndex,
            probeOff = s.probeOff,
            pulseSearching = s.pulseSearching
        )
    }

    fun isPc60HeartRateAuthoritative(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = Pc60StatusStore.load(context)
        return SourcePriorityPolicy.isPc60HeartRateAuthoritative(
            nowMs = nowMs,
            lastPacketAt = s.lastPacketAt,
            heartRate = s.heartRate,
            perfusionIndex = s.perfusionIndex,
            probeOff = s.probeOff,
            pulseSearching = s.pulseSearching
        )
    }

    fun isPc60Authoritative(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean =
        isPc60Spo2Authoritative(context, nowMs) && isPc60HeartRateAuthoritative(context, nowMs)

    fun activeSourceLabel(context: Context, nowMs: Long = System.currentTimeMillis()): String {
        val spo2Pc60 = isPc60Spo2Authoritative(context, nowMs)
        val hrPc60 = isPc60HeartRateAuthoritative(context, nowMs)
        return when {
            spo2Pc60 && hrPc60 -> "PC-60FW"
            spo2Pc60 -> "SpO₂: PC-60FW • Nabız: Galaxy Watch"
            hrPc60 -> "SpO₂: Galaxy Watch • Nabız: PC-60FW"
            else -> "Galaxy Watch"
        }
    }
}
