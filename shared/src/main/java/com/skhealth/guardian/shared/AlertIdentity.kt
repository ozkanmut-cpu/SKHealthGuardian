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

    /** Basic storage-safety validation retained for migration/legacy callers. */
    fun isValid(value: String?): Boolean = !value.isNullOrBlank() && value.length <= 256

    /**
     * Strict current exact identity validation.
     * Requires a known alert type and a stable non-timestamp suffix. Legacy `:ts:` identities are
     * deliberately excluded because they can collide when wall clock moves or two events share a
     * timestamp.
     */
    fun isExact(value: String?): Boolean {
        if (!isValid(value)) return false
        val normalized = value!!.trim()
        val separator = normalized.indexOf(':')
        if (separator <= 0 || separator == normalized.lastIndex) return false
        val typeName = normalized.substring(0, separator)
        if (AlertType.entries.none { it.name == typeName }) return false
        val suffix = normalized.substring(separator + 1)
        if (suffix.isBlank() || suffix.startsWith("ts:")) return false
        if (suffix == "event:" || suffix.startsWith("event:") && suffix.removePrefix("event:").isBlank()) return false
        return true
    }
}
