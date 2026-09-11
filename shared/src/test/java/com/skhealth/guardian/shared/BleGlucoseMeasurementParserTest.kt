package com.skhealth.guardian.shared

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BleGlucoseMeasurementParserTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `parses standard kg per liter glucose packet into mg dl`() {
        // flags: concentration present, kg/L
        // sequence: 42
        // base time: 2026-09-11 08:00:00 UTC
        // SFLOAT: 112 * 10^-5 kg/L = 0.00112 kg/L = 112 mg/dL
        // type/location: capillary whole blood / finger (test fixture values)
        val payload = byteArrayOf(
            0x02,
            0x2A, 0x00,
            0xEA.toByte(), 0x07,
            0x09, 0x0B, 0x08, 0x00, 0x00,
            0x70, 0xB0.toByte(),
            0x11
        )

        val parsed = BleGlucoseMeasurementParser.parse(payload, utc)

        assertEquals(42, parsed.sequenceNumber)
        assertEquals(112, parsed.valueMgDl)
        assertEquals(BleGlucoseMeasurementParser.Unit.KG_PER_L, parsed.unit)
        assertEquals(1, parsed.type)
        assertEquals(1, parsed.sampleLocation)
        assertFalse(parsed.contextFollows)

        val expected = GregorianCalendar(utc).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 11, 8, 0, 0)
        }.timeInMillis
        assertEquals(expected, parsed.measuredAtMs)
    }

    @Test
    fun `applies optional signed time offset`() {
        val payload = byteArrayOf(
            0x01,
            0x01, 0x00,
            0xEA.toByte(), 0x07,
            0x09, 0x0B, 0x08, 0x00, 0x00,
            0x1E, 0x00 // +30 minutes
        )

        val parsed = BleGlucoseMeasurementParser.parse(payload, utc)
        val base = GregorianCalendar(utc).apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 11, 8, 0, 0)
        }.timeInMillis

        assertEquals(base + 30L * 60_000L, parsed.measuredAtMs)
        assertNull(parsed.valueMgDl)
    }

    @Test
    fun `decodes signed sfloat and reserved values`() {
        // 112 * 10^-5
        assertEquals(0.00112, BleGlucoseMeasurementParser.sfloat(byteArrayOf(0x70, 0xB0.toByte()), 0)!!, 0.0000001)
        assertNull(BleGlucoseMeasurementParser.sfloat(byteArrayOf(0xFF.toByte(), 0x07), 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated packet`() {
        BleGlucoseMeasurementParser.parse(byteArrayOf(0x00, 0x01), utc)
    }
}
