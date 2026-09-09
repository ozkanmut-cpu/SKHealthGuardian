package com.skhealth.guardian.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.skhealth.guardian.shared.HealthReading

class PhoneBridge(private val context: Context) {
    private val prefs = context.getSharedPreferences("phone_bridge_queue", Context.MODE_PRIVATE)

    suspend fun send(reading: HealthReading) {
        val payload = encode(reading)
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.awaitCompat() }.getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            enqueue(payload)
            return
        }

        flushQueued(nodes.map { it.id })
        val ok = nodes.all { node ->
            runCatching { Wearable.getMessageClient(context).sendMessage(node.id, "/health/reading", payload).awaitCompat() }.isSuccess
        }
        if (!ok) enqueue(payload)
    }

    private suspend fun flushQueued(nodeIds: List<String>) {
        val queued = loadQueue().toMutableList()
        if (queued.isEmpty()) return
        val remaining = mutableListOf<String>()
        queued.forEach { payload ->
            val bytes = payload.toByteArray()
            val ok = nodeIds.all { id ->
                runCatching { Wearable.getMessageClient(context).sendMessage(id, "/health/reading", bytes).awaitCompat() }.isSuccess
            }
            if (!ok) remaining += payload
        }
        saveQueue(remaining)
    }

    private fun encode(reading: HealthReading): String = listOf(
        reading.id,
        reading.timestampMs.toString(),
        reading.spo2?.toString().orEmpty(),
        reading.heartRate?.toString().orEmpty(),
        reading.valid.toString(),
        reading.source
    ).joinToString("|")

    private fun enqueue(payload: String) {
        val q = loadQueue().toMutableList()
        if (q.lastOrNull() != payload) q += payload
        saveQueue(q.takeLast(MAX_QUEUE))
    }

    private fun loadQueue(): List<String> = prefs.getString(KEY_QUEUE, "")
        .orEmpty().lines().filter { it.isNotBlank() }

    private fun saveQueue(items: List<String>) {
        prefs.edit().putString(KEY_QUEUE, items.joinToString("\n")).apply()
    }

    companion object {
        private const val KEY_QUEUE = "pending_readings"
        private const val MAX_QUEUE = 500
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
        addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.failure(it)) }
    }
