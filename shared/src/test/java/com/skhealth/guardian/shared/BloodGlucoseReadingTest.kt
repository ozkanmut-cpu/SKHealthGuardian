package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodGlucoseReadingTest {
    @Test
    fun `new meter readings are unconfirmed and cannot affect Orko state`() {
        val reading = BloodGlucoseReading(
            measuredAtMs = 1_000L,
            valueMgDl = 112,
            deviceId = "meter-a",
            sequenceNumber = 7
        )

        assertEquals(BloodGlucoseReading.Ownership.UNCONFIRMED, reading.ownership)
        assertFalse(reading.belongsToOrko)
    }

    @Test
    fun `ownership confirmation is explicit`() {
        val reading = BloodGlucoseReading(
            measuredAtMs = 1_000L,
            valueMgDl = 112
        )

        val orko = reading.confirmOwnership(
            BloodGlucoseReading.Ownership.ORKO,
            confirmedAtMs = 2_000L
        )
        val other = reading.confirmOwnership(
            BloodGlucoseReading.Ownership.OTHER_PERSON,
            confirmedAtMs = 2_000L
        )

        assertTrue(orko.belongsToOrko)
        assertFalse(other.belongsToOrko)
        assertEquals(2_000L, orko.ownershipConfirmedAtMs)
    }

    @Test
    fun `meter sequence number is preferred for dedupe`() {
        val a = BloodGlucoseReading(
            measuredAtMs = 10_000L,
            valueMgDl = 101,
            deviceId = "accu-chek",
            sequenceNumber = 42
        )
        val replay = a.copy(
            id = "different-id",
            measuredAtMs = 20_000L,
            ownership = BloodGlucoseReading.Ownership.ORKO
        )

        assertEquals(a.dedupeKey(), replay.dedupeKey())
    }

    @Test
    fun `fallback dedupe distinguishes measurements without sequence number`() {
        val a = BloodGlucoseReading(
            measuredAtMs = 10_000L,
            valueMgDl = 101,
            deviceId = "accu-chek"
        )
        val b = a.copy(id = "b", measuredAtMs = 11_000L)

        assertNotEquals(a.dedupeKey(), b.dedupeKey())
    }
}
