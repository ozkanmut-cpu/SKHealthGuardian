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
            if (parts.size < 4) return null
            val seq = parts[1].toLongOrNull() ?: return null
            val alertId = parts[2]
            val alertTimestampMs = parts[3].toLongOrNull() ?: return null
            if (seq <= 0L || alertTimestampMs <= 0L || !isExactAlertId(alertId)) return null
            "v2|$seq|$alertId"
        } else {
            val timestamp = parts.firstOrNull()?.toLongOrNull() ?: return null
            if (timestamp <= 0L) return null
            "legacy|$timestamp"
        }
    }

    fun matchesPending(pendingAckPayload: String?, receiptPayload: String): Boolean {
        if (pendingAckPayload.isNullOrBlank() || receiptPayload.isBlank()) return false
        return receiptForAck(pendingAckPayload) == receiptPayload
    }

    private fun isExactAlertId(alertId: String): Boolean {
        if (!AlertIdentity.isValid(alertId)) return false
        val typeName = alertId.substringBefore(':', missingDelimiterValue = "")
        if (typeName.isBlank()) return false
        return AlertType.entries.any { it.name == typeName }
    }
}
