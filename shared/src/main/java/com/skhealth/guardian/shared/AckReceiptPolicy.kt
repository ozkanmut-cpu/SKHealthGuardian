package com.skhealth.guardian.shared

/**
 * Creates and validates phone receipts for pending Watch alarm acknowledgements.
 * A receipt must identify the exact pending ACK, not merely have an equal/newer numeric order.
 * This prevents delayed, duplicated or cross-protocol receipts from clearing a newer pending ACK.
 */
object AckReceiptPolicy {
    fun receiptForAck(ackPayload: String): String? {
        val parts = ackPayload.split('|', limit = 5)
        return if (parts.firstOrNull() == "v2") {
            val seq = parts.getOrNull(1)?.toLongOrNull() ?: return null
            val alertId = parts.getOrNull(2).orEmpty()
            if (seq <= 0L || !AlertIdentity.isValid(alertId)) return null
            "v2|$seq|$alertId"
        } else {
            val timestamp = ackPayload.substringBefore('|').toLongOrNull() ?: return null
            if (timestamp <= 0L) return null
            "legacy|$timestamp"
        }
    }

    fun matchesPending(pendingAckPayload: String?, receiptPayload: String): Boolean {
        if (pendingAckPayload.isNullOrBlank() || receiptPayload.isBlank()) return false
        return receiptForAck(pendingAckPayload) == receiptPayload
    }
}
