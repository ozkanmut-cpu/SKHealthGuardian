package com.skhealth.guardian.shared

object TechnicalAlertWireCodec {
    private const val VERSION = "v2"
    private const val MAX_EVENT_ID = 128
    private const val MAX_MESSAGE = 160

    fun encode(alert: AlertEvent): String = listOf(
        VERSION,
        alert.eventId.trim(),
        alert.type.name,
        alert.timestampMs.toString(),
        sanitize(alert.message)
    ).joinToString("|")

    fun decode(raw: String): AlertEvent? {
        val p = raw.split('|', limit = 5)
        if (p.size < 5 || p[0] != VERSION) return null
        val eventId = p[1].trim()
        if (eventId.isBlank() || eventId.length > MAX_EVENT_ID) return null
        val type = runCatching { AlertType.valueOf(p[2]) }.getOrNull() ?: return null
        val timestampMs = p[3].toLongOrNull()?.takeIf { it > 0L } ?: return null
        return AlertEvent(
            type = type,
            timestampMs = timestampMs,
            reading = null,
            message = p[4].ifBlank { "Saat teknik sağlık alarmı" },
            eventId = eventId
        )
    }

    private fun sanitize(value: String): String =
        value.replace('|', ' ').replace('\n', ' ').take(MAX_MESSAGE)
}
