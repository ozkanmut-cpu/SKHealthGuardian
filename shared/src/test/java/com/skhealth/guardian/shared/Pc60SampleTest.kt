package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class Pc60SampleTest {
    @Test
    fun stableSampleIsValid() {
        val s = Pc60Sample(1L, 96, 78, 4.2, probeOff = false, pulseSearching = false)
        assertTrue(s.valid)
        val r = s.toHealthReading()
        assertEquals(96, r.spo2)
        assertEquals(78, r.heartRate)
        assertEquals("pc60fw", r.source)
    }

    @Test
    fun probeOffIsInvalid() {
        assertFalse(Pc60Sample(1L, 96, 78, 4.2, probeOff = true, pulseSearching = false).valid)
    }

    @Test
    fun pulseSearchingIsInvalid() {
        assertFalse(Pc60Sample(1L, 96, 78, 4.2, probeOff = false, pulseSearching = true).valid)
    }

    @Test
    fun zeroPerfusionIndexIsInvalid() {
        assertFalse(Pc60Sample(1L, 96, 78, 0.0, probeOff = false, pulseSearching = false).valid)
    }

    @Test
    fun zeroSpo2IsInvalid() {
        assertFalse(Pc60Sample(1L, 0, 78, 4.2, probeOff = false, pulseSearching = false).valid)
    }
}
