package com.skhealth.guardian.mobile

import android.content.Context
import com.google.android.gms.wearable.Wearable

class WatchCommandSender(private val context: Context) {
    fun requestMeasurement() {
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, "/health/measure_now", ByteArray(0))
            }
        }
    }
}
