package com.skhealth.guardian.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertDedupPolicyTest {
    private val now = 300_000L

    @Test
    fun duplicateSpo2WithinCooldownIsBlocked() {
        assertFalse(
            AlertDedupPolicy.shouldDispatch(
                AlertType.SPO2_LOW_CONFIRMED,
                now,
                now - 60_000L,
                86,
                85
            )
        )
    }

    @Test
    fun spo2DeteriorationOfThreePointsBypassesCooldown() {
        assertTrue(
            AlertDedupPolicy.shouldDispatch(
                AlertType.SPO2_LOW_CONFIRMED,
                now,
                now - 60_000L,
                86,
                83
            )
        )
    }

    @Test
    fun highHeartRateDeteriorationOfFifteenBypassesCooldown() {
        assertTrue(
            AlertDedupPolicy.shouldDispatch(
                AlertType.HEART_RATE_HIGH_CONFIRMED,
                now,
                now - 60_000L,
                135,
                150
            )
        )
    }

    @Test
    fun lowHeartRateDeteriorationOfTenBypassesCooldown() {
        assertTrue(
            AlertDedupPolicy.shouldDispatch(
                AlertType.HEART_RATE_LOW_CONFIRMED,
                now,
                now - 60_000L,
                45,
                35
            )
        )
    }

    @Test
    fun sameAlertAfterCooldownIsAllowed() {
        assertTrue(
            AlertDedupPolicy.shouldDispatch(
                AlertType.SPO2_LOW_CONFIRMED,
                now,
                now - AlertDedupPolicy.DEFAULT_COOLDOWN_MS,
                86,
                86
            )
        )
    }
}
