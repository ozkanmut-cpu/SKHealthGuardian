package com.skhealth.guardian.shared

/** Parser for Bluetooth SIG Glucose Measurement Context (0x2A34). */
object BleGlucoseContextParser {
    private const val FLAG_CARBOHYDRATE = 0x01
    private const val FLAG_MEAL = 0x02
    private const val FLAG_TESTER_HEALTH = 0x04
    private const val FLAG_EXERCISE = 0x08
    private const val FLAG_MEDICATION = 0x10
    private const val FLAG_MEDICATION_UNITS_LITERS = 0x20
    private const val FLAG_HBA1C = 0x40
    private const val FLAG_EXTENDED = 0x80

    data class ParsedContext(
        val sequenceNumber: Int,
        val meal: Int?,
        val tester: Int?,
        val health: Int?,
        val exerciseDurationSeconds: Int?,
        val exerciseIntensityPercent: Int?,
        val medicationId: Int?,
        val medicationAmountRaw: Double?,
        val medicationUnitsLiters: Boolean?,
        val hba1cPercent: Double?
    ) {
        /** Maps only standardized meal context to Orko's coarse context model. */
        fun toMeasurementContext(): BloodGlucoseReading.MeasurementContext = when (meal) {
            1 -> BloodGlucoseReading.MeasurementContext.PRE_MEAL
            2 -> BloodGlucoseReading.MeasurementContext.POST_MEAL
            3 -> BloodGlucoseReading.MeasurementContext.FASTING
            5 -> BloodGlucoseReading.MeasurementContext.BEDTIME
            4 -> BloodGlucoseReading.MeasurementContext.OTHER
            else -> BloodGlucoseReading.MeasurementContext.UNKNOWN
        }
    }

    fun parse(payload: ByteArray): ParsedContext {
        require(payload.size >= 3) { "Glucose context payload is too short" }
        var offset = 0
        val flags = u8(payload[offset++])
        val sequenceNumber = u16(payload, offset)
        offset += 2

        if (flags and FLAG_EXTENDED != 0) {
            require(payload.size > offset) { "Missing extended flags" }
            offset += 1
        }

        if (flags and FLAG_CARBOHYDRATE != 0) {
            require(payload.size >= offset + 3) { "Missing carbohydrate fields" }
            offset += 1 // carbohydrate id
            offset += 2 // carbohydrate amount SFLOAT
        }

        var meal: Int? = null
        if (flags and FLAG_MEAL != 0) {
            require(payload.size > offset) { "Missing meal field" }
            meal = u8(payload[offset++])
        }

        var tester: Int? = null
        var health: Int? = null
        if (flags and FLAG_TESTER_HEALTH != 0) {
            require(payload.size > offset) { "Missing tester/health field" }
            val testerHealth = u8(payload[offset++])
            tester = testerHealth and 0x0F
            health = (testerHealth ushr 4) and 0x0F
        }

        var exerciseDuration: Int? = null
        var exerciseIntensity: Int? = null
        if (flags and FLAG_EXERCISE != 0) {
            require(payload.size >= offset + 3) { "Missing exercise fields" }
            exerciseDuration = u16(payload, offset)
            offset += 2
            exerciseIntensity = u8(payload[offset++])
        }

        var medicationId: Int? = null
        var medicationAmountRaw: Double? = null
        var medicationUnitsLiters: Boolean? = null
        if (flags and FLAG_MEDICATION != 0) {
            require(payload.size >= offset + 3) { "Missing medication fields" }
            medicationId = u8(payload[offset++])
            medicationAmountRaw = BleGlucoseMeasurementParser.sfloat(payload, offset)
            offset += 2
            medicationUnitsLiters = flags and FLAG_MEDICATION_UNITS_LITERS != 0
        }

        var hba1c: Double? = null
        if (flags and FLAG_HBA1C != 0) {
            require(payload.size >= offset + 2) { "Missing HbA1c field" }
            hba1c = BleGlucoseMeasurementParser.sfloat(payload, offset)
            offset += 2
        }

        require(offset <= payload.size) { "Malformed glucose context payload" }

        return ParsedContext(
            sequenceNumber = sequenceNumber,
            meal = meal,
            tester = tester,
            health = health,
            exerciseDurationSeconds = exerciseDuration,
            exerciseIntensityPercent = exerciseIntensity,
            medicationId = medicationId,
            medicationAmountRaw = medicationAmountRaw,
            medicationUnitsLiters = medicationUnitsLiters,
            hba1cPercent = hba1c
        )
    }

    private fun u8(value: Byte): Int = value.toInt() and 0xFF
    private fun u16(payload: ByteArray, offset: Int): Int =
        u8(payload[offset]) or (u8(payload[offset + 1]) shl 8)
}
