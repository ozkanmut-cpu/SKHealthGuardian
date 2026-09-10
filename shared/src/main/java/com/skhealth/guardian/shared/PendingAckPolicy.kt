package com.skhealth.guardian.shared

object PendingAckPolicy {
    fun newest(existingPayload: String?, incomingPayload: String): String {
        if (existingPayload.isNullOrBlank()) return incomingPayload

        val existingV2 = isV2(existingPayload)
        val incomingV2 = isV2(incomingPayload)

        // Once exact-ID v2 ACKs exist, a legacy timestamp ACK must never replace them.
        // Conversely, a new v2 ACK must supersede any legacy payload left from migration.
        if (existingV2 != incomingV2) return if (incomingV2) incomingPayload else existingPayload

        val existingOrder = order(existingPayload)
        val incomingOrder = order(incomingPayload)
        return if (incomingOrder >= existingOrder) incomingPayload else existingPayload
    }

    fun order(payload: String): Long {
        val parts = payload.split('|', limit = 5)
        return if (parts.firstOrNull() == "v2") {
            parts.getOrNull(1)?.toLongOrNull() ?: Long.MIN_VALUE
        } else {
            payload.substringBefore('|').toLongOrNull() ?: Long.MIN_VALUE
        }
    }

    fun isV2(payload: String): Boolean = payload.startsWith("v2|")
}
