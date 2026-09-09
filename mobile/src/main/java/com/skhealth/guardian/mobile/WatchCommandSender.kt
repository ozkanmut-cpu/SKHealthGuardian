package com.skhealth.guardian.mobile

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.skhealth.guardian.shared.AlarmConfig

class WatchCommandSender(private val context: Context) {
    fun requestMeasurement() = send("/health/measure_now", ByteArray(0))

    fun sendConfig(config: AlarmConfig) {
        val payload = listOf(
            config.spo2CriticalImmediate,
            config.spo2LowThreshold,
            config.spo2ConfirmCount,
            config.heartRateHighThreshold,
            config.heartRateHighConfirmCount,
            config.heartRateLowEnabled,
            config.heartRateLowThreshold,
            config.heartRateLowConfirmCount,
            config.staleDataMs
        ).joinToString("|").toByteArray()
        send("/health/config", payload)
    }

    private fun send(path: String, payload: ByteArray) {
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, path, payload)
            }
        }
    }
}
