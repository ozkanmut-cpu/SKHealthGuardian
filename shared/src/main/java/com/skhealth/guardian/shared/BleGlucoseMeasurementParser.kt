package com.skhealth.guardian.shared

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.pow
import kotlin.math.roundToInt

/** Parser for the Bluetooth SIG Glucose Measurement characteristic (0x2A18). */
object BleGlucoseMeasurementParser {
    const val GLUCOSE_SERVICE_UUID = "00001808-0000-1000-8000-00805f9b34fb"
    const val GLUCOSE_MEASUREMENT_UUID = "00002a18-0000-1000-8000-00805f9b34fb"
    const val GLUCOSE_MEASUREMENT_CONTEXT_UUID = "00002a34-0000-1000-8000-00805f9b34fb"
    const val GLUCOSE_FEATURE_UUID = "00002a51-0000-1000-8000-00805f9b34fb"
    const val RECORD_ACCESS_CONTROL_POINT_UUID = "00002a52-0000-1000-8000-00805f9b34fb"

    private const val FLAG_TIME_OFFSET = 0x01
    private const val FLAG_CONCENTRATION = 0x02
    private const val FLAG_UNITS_MOL_PER_L = 0x04
    private const val FLAG_SENSOR_STATUS = 0x08
    private const val FLAG_CONTEXT_FOLLOWS = 0x10

    enum class Unit { KG_PER_L, MOL_PER_L }

    data class ParsedMeasurement(
        val sequenceNumber: Int,
        val measuredAtMs: Long,
        val concentrationRaw: Double?,
        val unit: Unit?,
        val valueMgDl: Int?,
        val type: Int?,
        val sampleLocation: Int?,
        val sensorStatusAnnunciation: Int?,
        val contextFollows: Boolean
    )

    fun parse(payload: ByteArray, timeZone: TimeZone = TimeZone.getDefault()): ParsedMeasurement {
        require(payload.size >= 10) { "Glucose measurement payload is too short" }
        var offset = 0
        val flags = u8(payload[offset++])
        val sequenceNumber = u16(payload, offset)
        offset += 2

        val year = u16(payload, offset)
        offset += 2
        val month = u8(payload[offset++])
        val day = u8(payload[offset++])
        val hour = u8(payload[offset++])
        val minute = u8(payload[offset++])
        val second = u8(payload[offset++])

        require(year in 1582..9999) { "Invalid glucose measurement year" }
        require(month in 1..12) { "Invalid glucose measurement month" }
        require(day in 1..31) { "Invalid glucose measurement day" }
        require(hour in 0..23 && minute in 0..59 && second in 0..59) {
            "Invalid glucose measurement time"
        }

        var measuredAtMs = toEpochMs(year, month, day, hour, minute, second, timeZone)

        if (flags and FLAG_TIME_OFFSET != 0) {
            require(payload.size >= offset + 2) { "Missing glucose time offset" }
            val timeOffsetMinutes = s16(payload, offset)
            offset += 2
            measuredAtMs += timeOffsetMinutes * 60_000L
        }

        var rawConcentration: Double? = null
        var unit: Unit? = null
        var valueMgDl: Int? = null
        var type: Int? = null
        var sampleLocation: Int? = null

        if (flags and FLAG_CONCENTRATION != 0) {
            require(payload.size >= offset + 3) { "Missing glucose concentration fields" }
            rawConcentration = sfloat(payload, offset)
            offset += 2
            val typeAndLocation = u8(payload[offset++])
            type = typeAndLocation and 0x0F
            sampleLocation = (typeAndLocation ushr 4) and 0x0F
            unit = if (flags and FLAG_UNITS_MOL_PER_L != 0) Unit.MOL_PER_L else Unit.KG_PER_L
            valueMgDl = rawConcentration?.let { concentrationToMgDl(it, unit) }
        }

        var sensorStatus: Int? = null
        if (flags and FLAG_SENSOR_STATUS != 0) {
            require(payload.size >= offset + 2) { "Missing sensor status annunciation" }
            sensorStatus = u16(payload, offset)
            offset += 2
        }

        require(offset <= payload.size) { "Malformed glucose measurement payload" }

        return ParsedMeasurement(
            sequenceNumber = sequenceNumber,
            measuredAtMs = measuredAtMs,
            concentrationRaw = rawConcentration,
            unit = unit,
            valueMgDl = valueMgDl,
            type = type,
            sampleLocation = sampleLocation,
            sensorStatusAnnunciation = sensorStatus,
            contextFollows = flags and FLAG_CONTEXT_FOLLOWS != 0
        )
    }

    fun concentrationToMgDl(value: Double, unit: Unit): Int = when (unit) {
        // Bluetooth Glucose Service concentration is kg/L when unit flag is 0.
        Unit.KG_PER_L -> (value * 100_000.0).roundToInt()
        // Molecular glucose mass ~= 180.15588 g/mol -> 18,015.588 mg/dL per mol/L.
        Unit.MOL_PER_L -> (value * 18_015.588).roundToInt()
    }

    /** Bluetooth IEEE-11073 16-bit SFLOAT. Returns null for special/reserved values. */
    fun sfloat(payload: ByteArray, offset: Int): Double? {
        require(payload.size >= offset + 2) { "Missing SFLOAT" }
        val bits = u16(payload, offset)
        if (bits in setOf(0x07FF, 0x0800, 0x0801, 0x0802, 0x07FE)) return null

        var mantissa = bits and 0x0FFF
        if (mantissa and 0x0800 != 0) mantissa -= 0x1000
        var exponent = (bits ushr 12) and 0x0F
        if (exponent and 0x08 != 0) exponent -= 0x10
        return mantissa * 10.0.pow(exponent.toDouble())
    }

    private fun toEpochMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        timeZone: TimeZone
    ): Long {
        val calendar: Calendar = GregorianCalendar(timeZone).apply {
            isLenient = false
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        return calendar.timeInMillis
    }

    private fun u8(value: Byte): Int = value.toInt() and 0xFF
    private fun u16(payload: ByteArray, offset: Int): Int =
        u8(payload[offset]) or (u8(payload[offset + 1]) shl 8)

    private fun s16(payload: ByteArray, offset: Int): Int {
        val value = u16(payload, offset)
        return if (value and 0x8000 != 0) value - 0x10000 else value
    }
}
