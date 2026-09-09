package com.skhealth.guardian.shared

/**
 * Stable identity for one concrete alarm instance.
 *
 * Measurement-backed alarms use the reading UUID rather than sensor wall-clock time. Technical
 * alarms use AlertEvent.eventId, so current exact-ID flows no longer depend on wall-clock time.
 * The timestamp overload remains for backward compatibility with legacy callers/tests.
 */
object AlertIdentity {
    fun of(alert: AlertEvent): String {
        val stableReadingId = alert.reading?.id?.trim().orEmpty()
        if (stableReadingId.isNotEmpty()) return "${alert.type.name}:$stableReadingId"

        val stableEventId = alert.eventId.trim()
        return if (stableEventId.isNotEmpty()) {
            "${alert.type.name}:event:$stableEventId"
        } else {
            "${alert.type.name}:ts:${alert.timestampMs}"
        }
    }

    fun of(type: AlertType, readingId: String?, timestampMs: Long): String {
        val stableReadingId = readingId?.trim().orEmpty()
        return if (stableReadingId.isNotEmpty()) {
            "${type.name}:$stableReadingId"
        } else {
            "${type.name}:ts:$timestampMs"
        }
    }

    fun isValid(value: String?): Boolean = !value.isNullOrBlank() && value.length <= 256
}
