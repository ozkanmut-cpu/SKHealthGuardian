package com.skhealth.guardian.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.skhealth.guardian.shared.AckReceiptPolicy
import com.skhealth.guardian.shared.AlertEvent
import com.skhealth.guardian.shared.HealthReading
import com.skhealth.guardian.shared.PendingAckPolicy
import com.skhealth.guardian.shared.TechnicalAlertWireCodec

class PhoneBridge(private val context: Context) {
    private val prefs = context.getSharedPreferences("phone_bridge_queue", Context.MODE_PRIVATE)

    suspend fun send(reading: HealthReading) {
        val payload = encode(reading)
        val bytes = payload.toByteArray()
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.awaitCompat() }.getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            enqueueReading(payload)
            return
        }

        flushAlarmAcknowledgement(nodes.map { it.id })
        flushQueuedReadings(nodes.map { it.id })
        flushQueuedAlerts(nodes.map { it.id })
        val ok = nodes.all { node ->
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/health/reading", bytes)
                    .awaitCompat()
            }.isSuccess
        }
        if (!ok) enqueueReading(payload)
    }

    suspend fun sendAlert(alert: AlertEvent) {
        val payload = TechnicalAlertWireCodec.encode(alert)
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.awaitCompat() }.getOrDefault(emptyList())
        if (nodes.isEmpty()) {
            enqueueAlert(payload)
            return
        }
        flushAlarmAcknowledgement(nodes.map { it.id })
        flushQueuedAlerts(nodes.map { it.id })
        val bytes = payload.toByteArray()
        val ok = nodes.all { node ->
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/health/alert", bytes)
                    .awaitCompat()
            }.isSuccess
        }
        if (!ok) enqueueAlert(payload)
    }

    suspend fun sendHeartbeat(batteryPct: Int) {
        val payload = "${System.currentTimeMillis()}|$batteryPct".toByteArray()
        val nodes = runCatching { Wearable.getNodeClient(context).connectedNodes.awaitCompat() }.getOrDefault(emptyList())
        if (nodes.isNotEmpty()) {
            flushAlarmAcknowledgement(nodes.map { it.id })
            flushQueuedReadings(nodes.map { it.id })
            flushQueuedAlerts(nodes.map { it.id })
        }
        nodes.forEach { node ->
            runCatching { Wearable.getMessageClient(context).sendMessage(node.id, "/health/heartbeat", payload).awaitCompat() }
        }
    }

    fun sendAlarmAcknowledgement(alertId: String, alertTimestampMs: Long, reason: String): Boolean {
        val safeReason = sanitizeReason(reason)
        val seq = nextAckSequence() ?: return false
        return sendAckPayload("v2|$seq|$alertId|$alertTimestampMs|$safeReason")
    }

    fun sendAlarmAcknowledgement(alertTimestampMs: Long, reason: String): Boolean =
        sendAckPayload("$alertTimestampMs|${sanitizeReason(reason)}")

    private fun sendAckPayload(payload: String): Boolean {
        // Durably persist before any remote side effect. If this fails the Watch keeps the local
        // alarm active and does not tell the phone to cancel escalation; the user can retry.
        if (!savePendingAck(payload)) return false
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) return@addOnSuccessListener
                nodes.forEach { node ->
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, "/health/alarm_ack", payload.toByteArray())
                }
            }
        return true
    }

    fun confirmAlarmAcknowledgement(receiptPayload: String) {
        if (receiptPayload.isBlank()) return
        clearPendingAckIfMatches(receiptPayload)
    }

    private suspend fun flushQueuedReadings(nodeIds: List<String>) {
        val queued = synchronized(this) { loadQueue(KEY_READING_QUEUE) }
        if (queued.isEmpty()) return
        val delivered = mutableSetOf<String>()
        queued.forEach { payload ->
            val bytes = payload.toByteArray()
            val ok = nodeIds.all { id ->
                runCatching { Wearable.getMessageClient(context).sendMessage(id, "/health/reading", bytes).awaitCompat() }.isSuccess
            }
            if (ok) delivered += payload
        }
        if (delivered.isNotEmpty()) removeDelivered(KEY_READING_QUEUE, delivered)
    }

    private suspend fun flushQueuedAlerts(nodeIds: List<String>) {
        val queued = synchronized(this) { loadQueue(KEY_ALERT_QUEUE) }
        if (queued.isEmpty()) return
        val delivered = mutableSetOf<String>()
        queued.forEach { payload ->
            val bytes = payload.toByteArray()
            val ok = nodeIds.all { id ->
                runCatching { Wearable.getMessageClient(context).sendMessage(id, "/health/alert", bytes).awaitCompat() }.isSuccess
            }
            if (ok) delivered += payload
        }
        if (delivered.isNotEmpty()) removeDelivered(KEY_ALERT_QUEUE, delivered)
    }

    private suspend fun flushAlarmAcknowledgement(nodeIds: List<String>) {
        val payload = synchronized(this) { prefs.getString(KEY_PENDING_ACK, null) } ?: return
        val bytes = payload.toByteArray()
        nodeIds.forEach { id ->
            runCatching { Wearable.getMessageClient(context).sendMessage(id, "/health/alarm_ack", bytes).awaitCompat() }
        }
        // Deliberately retain until a durable receipt for this exact ACK arrives from the phone.
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
    private fun enqueueReading(payload: String) = enqueue(KEY_READING_QUEUE, payload, MAX_READING_QUEUE)

    @Synchronized
    private fun enqueueAlert(payload: String) = enqueue(KEY_ALERT_QUEUE, payload, MAX_ALERT_QUEUE)

    private fun enqueue(key: String, payload: String, max: Int) {
        val q = loadQueue(key).toMutableList()
        if (!q.contains(payload)) q += payload
        saveQueue(key, q.takeLast(max))
    }

    private fun loadQueue(key: String): List<String> = prefs.getString(key, "")
        .orEmpty().lines().filter { it.isNotBlank() }

    private fun saveQueue(key: String, items: List<String>) {
        prefs.edit().putString(key, items.joinToString("\n")).apply()
    }

    @Synchronized
    private fun removeDelivered(key: String, delivered: Set<String>) {
        val current = loadQueue(key)
        saveQueue(key, current.filterNot { it in delivered })
    }

    @Synchronized
    private fun savePendingAck(payload: String): Boolean {
        val current = prefs.getString(KEY_PENDING_ACK, null)
        return prefs.edit().putString(KEY_PENDING_ACK, PendingAckPolicy.newest(current, payload)).commit()
    }

    @Synchronized
    private fun clearPendingAckIfMatches(receiptPayload: String) {
        val current = prefs.getString(KEY_PENDING_ACK, null) ?: return
        if (AckReceiptPolicy.matchesPending(current, receiptPayload)) {
            prefs.edit().remove(KEY_PENDING_ACK).commit()
        }
    }

    @Synchronized
    private fun nextAckSequence(): Long? {
        val current = prefs.getLong(KEY_ACK_SEQUENCE, 0L)
        val next = if (current == Long.MAX_VALUE) 1L else current + 1L
        return if (prefs.edit().putLong(KEY_ACK_SEQUENCE, next).commit()) next else null
    }

    private fun sanitizeReason(reason: String): String =
        reason.replace('|', ' ').replace('\n', ' ').take(160)

    companion object {
        private const val KEY_READING_QUEUE = "pending_readings"
        private const val KEY_ALERT_QUEUE = "pending_alerts_v2"
        private const val KEY_PENDING_ACK = "pending_alarm_ack"
        private const val KEY_ACK_SEQUENCE = "alarm_ack_sequence"
        private const val MAX_READING_QUEUE = 500
        private const val MAX_ALERT_QUEUE = 100
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCompat(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) {} }
        addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.failure(it)) }
    }
