package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleGlucoseContextParserTest {
    @Test
    fun `parses post meal context and sequence number`() {
        val payload = byteArrayOf(
            0x02,       // meal present
            0x2A, 0x00, // sequence 42
            0x02        // postprandial
        )

        val parsed = BleGlucoseContextParser.parse(payload)

        assertEquals(42, parsed.sequenceNumber)
        assertEquals(2, parsed.meal)
        assertEquals(BloodGlucoseReading.MeasurementContext.POST_MEAL, parsed.toMeasurementContext())
    }

    @Test
    fun `maps fasting premeal bedtime and casual context`() {
        fun context(meal: Int) = BleGlucoseContextParser.parse(
            byteArrayOf(0x02, 0x01, 0x00, meal.toByte())
        ).toMeasurementContext()

        assertEquals(BloodGlucoseReading.MeasurementContext.PRE_MEAL, context(1))
        assertEquals(BloodGlucoseReading.MeasurementContext.POST_MEAL, context(2))
        assertEquals(BloodGlucoseReading.MeasurementContext.FASTING, context(3))
        assertEquals(BloodGlucoseReading.MeasurementContext.OTHER, context(4))
        assertEquals(BloodGlucoseReading.MeasurementContext.BEDTIME, context(5))
    }

    @Test
    fun `unknown meal remains unknown`() {
        val parsed = BleGlucoseContextParser.parse(byteArrayOf(0x00, 0x07, 0x00))
        assertNull(parsed.meal)
        assertEquals(BloodGlucoseReading.MeasurementContext.UNKNOWN, parsed.toMeasurementContext())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated meal context`() {
        BleGlucoseContextParser.parse(byteArrayOf(0x02, 0x01, 0x00))
    }
}
