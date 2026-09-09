package com.skhealth.guardian.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.skhealth.guardian.shared.HealthReading

class PhoneBridge(private val context: Context) {
    suspend fun send(reading: HealthReading) {
        val payload = listOf(
            reading.timestampMs.toString(),
            reading.spo2?.toString().orEmpty(),
            reading.heartRate?.toString().orEmpty(),
            reading.valid.toString(),
            reading.source
        ).joinToString("|").toByteArray()
        val nodes = Wearable.getNodeClient(context).connectedNodes.awaitCompat()
        nodes.forEach { node ->
            Wearable.getMessageClient(context).sendMessage(node.id, "/health/reading", payload).awaitCompat()
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
        addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.failure(it)) }
    }
