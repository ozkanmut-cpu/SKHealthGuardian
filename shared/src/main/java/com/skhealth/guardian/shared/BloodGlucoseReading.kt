package com.skhealth.guardian.shared

import java.util.UUID

/**
 * A glucose result imported from a meter.
 *
 * Ownership is deliberately independent from timing/context. A reading must
 * never affect Orko's history, schedule completion or alarms until ownership
 * is explicitly ORKO.
 */
data class BloodGlucoseReading(
    val id: String = UUID.randomUUID().toString(),
    val measuredAtMs: Long,
    val valueMgDl: Int,
    val ownership: Ownership = Ownership.UNCONFIRMED,
    val source: Source = Source.ACCU_CHEK_INSTANT,
    val deviceId: String? = null,
    val sequenceNumber: Int? = null,
    val checkpointHint: GlucoseScheduleEngine.Checkpoint? = null,
    val context: MeasurementContext = MeasurementContext.UNKNOWN,
    val importedAtMs: Long = measuredAtMs,
    val ownershipConfirmedAtMs: Long? = null
) {
    init {
        require(measuredAtMs > 0L) { "measuredAtMs must be positive" }
        require(valueMgDl in MIN_PLAUSIBLE_MG_DL..MAX_PLAUSIBLE_MG_DL) {
            "valueMgDl is outside supported meter range"
        }
        require(sequenceNumber == null || sequenceNumber >= 0) {
            "sequenceNumber must be non-negative"
        }
    }

    enum class Ownership {
        UNCONFIRMED,
        ORKO,
        OTHER_PERSON
    }

    enum class Source {
        ACCU_CHEK_INSTANT,
        MANUAL,
        IMPORTED
    }

    enum class MeasurementContext {
        UNKNOWN,
        FASTING,
        PRE_MEAL,
        POST_MEAL,
        BEDTIME,
        OTHER
    }

    /** Only confirmed Orko readings may enter Orko-specific calculations. */
    val belongsToOrko: Boolean
        get() = ownership == Ownership.ORKO

    /**
     * Stable identity for BLE replay/deduplication.
     * Prefer meter sequence number when available; otherwise use the measured
     * timestamp and value. Ownership/checkpoint are intentionally excluded so
     * confirming or reclassifying a reading does not create a duplicate.
     */
    fun dedupeKey(): String {
        val device = deviceId?.trim()?.takeIf { it.isNotEmpty() } ?: source.name
        return if (sequenceNumber != null) {
            "$device:seq:$sequenceNumber"
        } else {
            "$device:time:$measuredAtMs:value:$valueMgDl"
        }
    }

    fun confirmOwnership(
        owner: Ownership,
        confirmedAtMs: Long = System.currentTimeMillis()
    ): BloodGlucoseReading {
        require(owner != Ownership.UNCONFIRMED) { "Confirmation requires a concrete owner" }
        return copy(
            ownership = owner,
            ownershipConfirmedAtMs = confirmedAtMs
        )
    }

    companion object {
        // Broad defensive limits; clinical interpretation is handled elsewhere.
        const val MIN_PLAUSIBLE_MG_DL = 10
        const val MAX_PLAUSIBLE_MG_DL = 1000
    }
}
