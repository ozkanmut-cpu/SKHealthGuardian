package com.skhealth.guardian.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalAlertWireCodecTest {
    @Test
    fun roundTripPreservesExactEventIdentityAcrossClockRollback() {
        val event = AlertEvent(
            type = AlertType.SENSOR_FAILURE,
            timestampMs = 123L,
            reading = null,
            message = "Saat sensörü veri üretmiyor",
            eventId = "technical-event-uuid-1"
        )
        val decoded = TechnicalAlertWireCodec.decode(TechnicalAlertWireCodec.encode(event))!!
        assertEquals(event.eventId, decoded.eventId)
        assertEquals(event.type, decoded.type)
        assertEquals(event.timestampMs, decoded.timestampMs)
        assertEquals(AlertIdentity.of(event), AlertIdentity.of(decoded))
    }

    @Test
    fun encodeSanitizesDelimiterAndNewlineWithoutChangingIdentity() {
        val event = AlertEvent(
            type = AlertType.WATCH_DISCONNECTED,
            timestampMs = 999L,
            message = "satır1|satır2\nsatır3",
            eventId = "technical-event-uuid-2"
        )
        val raw = TechnicalAlertWireCodec.encode(event)
        val decoded = TechnicalAlertWireCodec.decode(raw)!!
        assertEquals(event.eventId, decoded.eventId)
        assertTrue('|' !in decoded.message)
        assertTrue('\n' !in decoded.message)
    }

    @Test
    fun malformedOrUnsupportedPayloadsAreRejected() {
        assertNull(TechnicalAlertWireCodec.decode(""))
        assertNull(TechnicalAlertWireCodec.decode("v1|id|SENSOR_FAILURE|123|msg"))
        assertNull(TechnicalAlertWireCodec.decode("v2||SENSOR_FAILURE|123|msg"))
        assertNull(TechnicalAlertWireCodec.decode("v2|id|NOPE|123|msg"))
        assertNull(TechnicalAlertWireCodec.decode("v2|id|SENSOR_FAILURE|0|msg"))
    }
}
