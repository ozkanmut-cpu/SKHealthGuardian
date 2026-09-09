package com.skhealth.guardian.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.shared.PendingAckPolicy

class PhoneBridge(private val context: Context) {
    private val prefs = context.getSharedPreferences("phone_bridge_queue", Context.MODE_PRIVATE)

    suspend fun send(reading: HealthReading) {
        val payload = encode(reading)
        val bytes = payload.toByteArray()
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.awaitCompat() }.getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            enqueue(payload)
            return
        }

        flushAlarmAcknowledgement(nodes.map { it.id })
        flushQueued(nodes.map { it.id })
        val ok = nodes.all { node ->
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/health/reading", bytes)
                    .awaitCompat()
            }.isSuccess
        }
        if (!ok) enqueue(payload)
    }

    suspend fun sendHeartbeat(batteryPct: Int) {
        val payload = "${System.currentTimeMillis()}|$batteryPct".toByteArray()
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.awaitCompat() }.getOrDefault(emptyList())
        if (nodes.isNotEmpty()) flushAlarmAcknowledgement(nodes.map { it.id })
        nodes.forEach { node ->
            runCatching { Wearable.getMessageClient(context).sendMessage(node.id, "/health/heartbeat", payload).awaitCompat() }
        }
    }

    fun sendAlarmAcknowledgement(alertId: String, alertTimestampMs: Long, reason: String) {
        val safeReason = sanitizeReason(reason)
        val seq = nextAckSequence()
        sendAckPayload("v2|$seq|$alertId|$alertTimestampMs|$safeReason")
    }

    fun sendAlarmAcknowledgement(alertTimestampMs: Long, reason: String) {
        sendAckPayload("$alertTimestampMs|${sanitizeReason(reason)}")
    }

    private fun sendAckPayload(payload: String) {
        val order = PendingAckPolicy.order(payload)
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    savePendingAck(payload)
                    return@addOnSuccessListener
                }
                var remaining = nodes.size
                var allOk = true
                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(node.id, "/health/alarm_ack", payload.toByteArray())
                        .addOnFailureListener { allOk = false }
                        .addOnCompleteListener {
                            remaining--
                            if (remaining == 0) {
                                if (allOk) clearPendingAckIfNotNewerThan(order) else savePendingAck(payload)
                            }
                        }
                }
            }
            .addOnFailureListener { savePendingAck(payload) }
    }

    private suspend fun flushQueued(nodeIds: List<String>) {
        val queued = synchronized(this) { loadQueue() }
        if (queued.isEmpty()) return
        val delivered = mutableSetOf<String>()
        queued.forEach { payload ->
            val bytes = payload.toByteArray()
            val ok = nodeIds.all { id ->
                runCatching { Wearable.getMessageClient(context).sendMessage(id, "/health/reading", bytes).awaitCompat() }.isSuccess
            }
            if (ok) delivered += payload
        }
        if (delivered.isNotEmpty()) removeDelivered(delivered)
    }

    private suspend fun flushAlarmAcknowledgement(nodeIds: List<String>) {
        val payload = synchronized(this) { prefs.getString(KEY_PENDING_ACK, null) } ?: return
        val bytes = payload.toByteArray()
        val ok = nodeIds.all { id ->
            runCatching { Wearable.getMessageClient(context).sendMessage(id, "/health/alarm_ack", bytes).awaitCompat() }.isSuccess
        }
        if (ok) clearPendingAckIfNotNewerThan(PendingAckPolicy.order(payload))
    }

    private fun encode(reading: HealthReading): String = listOf(
        reading.id,
        reading.timestampMs.toString(),
        reading.spo2?.toString().orEmpty(),
        reading.heartRate?.toString().orEmpty(),
        reading.valid.toString(),
        reading.source
    ).joinToString("|")

    @Synchronized
    private fun enqueue(payload: String) {
        val q = loadQueue().toMutableList()
        if (!q.contains(payload)) q += payload
        saveQueue(q.takeLast(MAX_QUEUE))
    }

    private fun loadQueue(): List<String> = prefs.getString(KEY_QUEUE, "")
        .orEmpty().lines().filter { it.isNotBlank() }

    private fun saveQueue(items: List<String>) {
        prefs.edit().putString(KEY_QUEUE, items.joinToString("\n")).apply()
    }

    @Synchronized
    private fun removeDelivered(delivered: Set<String>) {
        val current = loadQueue()
        saveQueue(current.filterNot { it in delivered })
    }

    @Synchronized
    private fun savePendingAck(payload: String) {
        val current = prefs.getString(KEY_PENDING_ACK, null)
        prefs.edit().putString(KEY_PENDING_ACK, PendingAckPolicy.newest(current, payload)).apply()
    }

    @Synchronized
    private fun clearPendingAckIfNotNewerThan(sentOrder: Long) {
        val current = prefs.getString(KEY_PENDING_ACK, null) ?: return
        if (PendingAckPolicy.order(current) <= sentOrder) prefs.edit().remove(KEY_PENDING_ACK).apply()
    }

    @Synchronized
    private fun nextAckSequence(): Long {
        val current = prefs.getLong(KEY_ACK_SEQUENCE, 0L)
        val next = if (current == Long.MAX_VALUE) 1L else current + 1L
        prefs.edit().putLong(KEY_ACK_SEQUENCE, next).commit()
        return next
    }

    private fun sanitizeReason(reason: String): String =
        reason.replace('|', ' ').replace('\n', ' ').take(160)

    companion object {
        private const val KEY_QUEUE = "pending_readings"
        private const val KEY_PENDING_ACK = "pending_alarm_ack"
        private const val KEY_ACK_SEQUENCE = "alarm_ack_sequence"
        private const val MAX_QUEUE = 500
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
        addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.failure(it)) }
    }
