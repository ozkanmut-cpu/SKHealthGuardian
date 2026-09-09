package com.skhealth.guardian.shared

object PendingAckPolicy {
    fun newest(existingPayload: String?, incomingPayload: String): String {
        if (existingPayload.isNullOrBlank()) return incomingPayload
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
}
