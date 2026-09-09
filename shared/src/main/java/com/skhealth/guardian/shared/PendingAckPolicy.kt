package com.skhealth.guardian.shared

object PendingAckPolicy {
    fun newest(existingPayload: String?, incomingPayload: String): String {
        if (existingPayload.isNullOrBlank()) return incomingPayload
        val existingTs = existingPayload.substringBefore('|').toLongOrNull() ?: Long.MIN_VALUE
        val incomingTs = incomingPayload.substringBefore('|').toLongOrNull() ?: Long.MIN_VALUE
        return if (incomingTs >= existingTs) incomingPayload else existingPayload
    }
}
