package com.skhealth.guardian.mobile

import android.content.Context
import com.skhealth.guardian.shared.SourcePriorityPolicy

object SourcePriorityCoordinator {
    private const val WATCH_HEARTBEAT_FRESH_MS = 150_000L

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

    fun isWatchConnected(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val heartbeatAt = WatchHeartbeatStore.timestamp(context)
        return heartbeatAt > 0L && nowMs >= heartbeatAt && nowMs - heartbeatAt <= WATCH_HEARTBEAT_FRESH_MS
    }

    fun activeSourceLabel(context: Context, nowMs: Long = System.currentTimeMillis()): String {
        val spo2Pc60 = isPc60Spo2Authoritative(context, nowMs)
        val hrPc60 = isPc60HeartRateAuthoritative(context, nowMs)
        val watchConnected = isWatchConnected(context, nowMs)
        return when {
            spo2Pc60 && hrPc60 -> "PC-60FW"
            spo2Pc60 && watchConnected -> "SpO₂: PC-60FW • Nabız: Galaxy Watch"
            hrPc60 && watchConnected -> "SpO₂: Galaxy Watch • Nabız: PC-60FW"
            spo2Pc60 -> "PC-60FW (SpO₂)"
            hrPc60 -> "PC-60FW (Nabız)"
            watchConnected -> "Galaxy Watch"
            else -> "Aktif kaynak yok"
        }
    }
}
