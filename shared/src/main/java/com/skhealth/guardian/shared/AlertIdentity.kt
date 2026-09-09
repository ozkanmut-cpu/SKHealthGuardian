package com.skhealth.guardian.shared

/**
 * Stable identity for one concrete alarm instance.
 *
 * Measurement-backed alarms use the reading UUID rather than sensor wall-clock time, so an
 * incorrect/rolled-back device clock cannot make a new alarm look older than a previous ACK.
 * Technical alarms without a reading retain a timestamp fallback because there is no reading ID.
 */
object AlertIdentity {
    fun of(alert: AlertEvent): String = of(alert.type, alert.reading?.id, alert.timestampMs)

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
