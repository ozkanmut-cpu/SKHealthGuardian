package com.skhealth.guardian.shared

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleGlucoseRacpTest {
    @Test
    fun `builds report all records command`() {
        assertArrayEquals(byteArrayOf(0x01, 0x01), BleGlucoseRacp.reportAllRecordsCommand())
    }

    @Test
    fun `builds sequence filtered sync command`() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x03, 0x01, 0x35, 0x12),
            BleGlucoseRacp.reportFromSequenceCommand(0x1235)
        )
    }

    @Test
    fun `parses successful response code`() {
        val response = BleGlucoseRacp.parseResponse(byteArrayOf(0x06, 0x00, 0x01, 0x01))
        assertEquals(0x01, response.requestOpcode)
        assertEquals(0x01, response.responseCode)
        assertTrue(response.success)
        assertFalse(response.noRecordsFound)
    }

    @Test
    fun `parses no records response`() {
        val response = BleGlucoseRacp.parseResponse(byteArrayOf(0x06, 0x00, 0x01, 0x06))
        assertTrue(response.noRecordsFound)
    }

    @Test
    fun `parses record count response`() {
        val response = BleGlucoseRacp.parseResponse(byteArrayOf(0x05, 0x00, 0x2A, 0x00))
        assertEquals(42, response.numberOfRecords)
    }
}
