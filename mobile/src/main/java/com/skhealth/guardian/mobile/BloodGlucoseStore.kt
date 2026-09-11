package com.skhealth.guardian.mobile

import android.content.Context
import android.util.Base64
import com.skhealth.guardian.shared.BloodGlucoseReading
import com.skhealth.guardian.shared.GlucoseScheduleEngine

/**
 * Durable glucose history that keeps ownership separate from measurement data.
 * Unconfirmed/other-person readings remain stored for reconciliation but are
 * excluded from Orko-specific queries by construction.
 */
object BloodGlucoseStore {
    private const val PREF = "blood_glucose_history"
    private const val KEY = "rows_v1"
    private const val MAX = 2_000
    private val lock = Any()

    fun addIfAbsent(context: Context, reading: BloodGlucoseReading): Boolean {
        val inserted = synchronized(lock) {
            val rows = readRows(context).toMutableList()
            val key = reading.dedupeKey()
            if (rows.any { it.dedupeKey() == key }) return@synchronized false
            rows += reading
            writeRows(context, rows.takeLast(MAX))
            true
        }
        if (inserted && reading.ownership == BloodGlucoseReading.Ownership.UNCONFIRMED) {
            GlucoseOwnershipNotification.show(context, reading)
        }
        return inserted
    }

    fun upsert(context: Context, reading: BloodGlucoseReading) = synchronized(lock) {
        val rows = readRows(context).toMutableList()
        val index = rows.indexOfFirst { it.id == reading.id || it.dedupeKey() == reading.dedupeKey() }
        if (index >= 0) rows[index] = reading else rows += reading
        writeRows(context, rows.takeLast(MAX))
    }

    fun confirmOwnership(
        context: Context,
        readingId: String,
        ownership: BloodGlucoseReading.Ownership,
        confirmedAtMs: Long = System.currentTimeMillis()
    ): BloodGlucoseReading? = synchronized(lock) {
        require(ownership != BloodGlucoseReading.Ownership.UNCONFIRMED) {
            "Ownership confirmation requires ORKO or OTHER_PERSON"
        }
        val rows = readRows(context).toMutableList()
        val index = rows.indexOfFirst { it.id == readingId }
        if (index < 0) return@synchronized null
        val updated = rows[index].confirmOwnership(ownership, confirmedAtMs)
        rows[index] = updated
        writeRows(context, rows)
        updated
    }

    /** Applies standardized BLE Measurement Context to the matching meter record. */
    fun updateContextBySequence(
        context: Context,
        deviceId: String?,
        sequenceNumber: Int,
        measurementContext: BloodGlucoseReading.MeasurementContext
    ): BloodGlucoseReading? = synchronized(lock) {
        val rows = readRows(context).toMutableList()
        val index = rows.indexOfLast {
            it.sequenceNumber == sequenceNumber &&
                (deviceId.isNullOrBlank() || it.deviceId == deviceId)
        }
        if (index < 0) return@synchronized null
        val current = rows[index]
        val updated = current.copy(
            context = if (measurementContext == BloodGlucoseReading.MeasurementContext.UNKNOWN) {
                current.context
            } else {
                measurementContext
            }
        )
        rows[index] = updated
        writeRows(context, rows)
        updated
    }

    /** Latest sequence imported from this meter, used as the next RACP sync anchor. */
    fun latestSequence(context: Context, deviceId: String?): Int? =
        readRows(context)
            .asSequence()
            .filter { deviceId.isNullOrBlank() || it.deviceId == deviceId }
            .mapNotNull { it.sequenceNumber }
            .maxOrNull()

    fun recent(context: Context, limit: Int = 100): List<BloodGlucoseReading> =
        readRows(context).takeLast(limit)

    fun pendingOwnership(context: Context, limit: Int = 100): List<BloodGlucoseReading> =
        readRows(context)
            .asSequence()
            .filter { it.ownership == BloodGlucoseReading.Ownership.UNCONFIRMED }
            .toList()
            .takeLast(limit)

    /** Safe source for Orko graphs, schedule completion and future alarm logic. */
    fun confirmedOrko(context: Context, limit: Int = 1_000): List<BloodGlucoseReading> =
        readRows(context)
            .asSequence()
            .filter { it.belongsToOrko }
            .toList()
            .takeLast(limit)

    fun findByDedupeKey(context: Context, dedupeKey: String): BloodGlucoseReading? =
        readRows(context).lastOrNull { it.dedupeKey() == dedupeKey }

    fun clear(context: Context) {
        synchronized(lock) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }

    private fun readRows(context: Context): List<BloodGlucoseReading> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.lineSequence().filter { it.isNotBlank() }.mapNotNull(::decode).toList()
    }

    private fun writeRows(context: Context, rows: List<BloodGlucoseReading>) {
        val raw = rows.joinToString("\n", transform = ::encode)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, raw).apply()
    }

    private fun encode(r: BloodGlucoseReading): String = listOf(
        r.id,
        r.measuredAtMs,
        r.valueMgDl,
        r.ownership.name,
        r.source.name,
        encodeText(r.deviceId),
        r.sequenceNumber ?: "",
        r.checkpointHint?.name ?: "",
        r.context.name,
        r.importedAtMs,
        r.ownershipConfirmedAtMs ?: ""
    ).joinToString("|")

    private fun decode(line: String): BloodGlucoseReading? {
        val p = line.split('|')
        if (p.size < 11) return null
        return runCatching {
            BloodGlucoseReading(
                id = p[0],
                measuredAtMs = p[1].toLong(),
                valueMgDl = p[2].toInt(),
                ownership = BloodGlucoseReading.Ownership.valueOf(p[3]),
                source = BloodGlucoseReading.Source.valueOf(p[4]),
                deviceId = decodeText(p[5]),
                sequenceNumber = p[6].toIntOrNull(),
                checkpointHint = p[7].takeIf { it.isNotBlank() }?.let(GlucoseScheduleEngine.Checkpoint::valueOf),
                context = BloodGlucoseReading.MeasurementContext.valueOf(p[8]),
                importedAtMs = p[9].toLong(),
                ownershipConfirmedAtMs = p[10].toLongOrNull()
            )
        }.getOrNull()
    }

    private fun encodeText(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decodeText(value: String): String? {
        if (value.isBlank()) return null
        return runCatching {
            String(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrNull()
    }
}
